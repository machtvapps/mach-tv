use std::future::Future;
use std::sync::LazyLock;

use chrono::{DateTime, Utc};
use futures::future;
use http::{Method, Request, StatusCode};
use serde::{Deserialize, Serialize};
use stremio_core::constants::SEARCH_HISTORY_STORAGE_KEY;
use stremio_core::models::ctx::Ctx;
use stremio_core::models::streaming_server::StreamingServer;
use stremio_core::runtime::{ConditionalSend, Env, EnvError, EnvFuture, EnvFutureExt, TryEnvFuture};

use crate::{executor, storage};

static HTTP: LazyLock<reqwest::Client> = LazyLock::new(|| {
    reqwest::Client::builder()
        .build()
        .expect("HTTP client failed to build")
});

pub enum AndroidEnv {}

impl Env for AndroidEnv {
    fn fetch<IN, OUT>(request: Request<IN>) -> TryEnvFuture<OUT>
    where
        IN: Serialize + ConditionalSend + 'static,
        OUT: for<'de> Deserialize<'de> + ConditionalSend + 'static,
    {
        let (parts, body) = request.into_parts();
        let body = match serde_json::to_string(&body) {
            Ok(body) if body != "null" && parts.method != Method::GET => Some(body),
            Ok(_) => None,
            Err(error) => return future::err(EnvError::from(error)).boxed_env(),
        };
        async move {
            let mut builder = HTTP
                .request(parts.method, parts.uri.to_string())
                .headers(parts.headers);
            if let Some(body) = body {
                builder = builder
                    .header(http::header::CONTENT_TYPE, "application/json")
                    .body(body);
            }
            let response = builder
                .send()
                .await
                .map_err(|error| EnvError::Fetch(error.to_string()))?;
            let status = response.status();
            if status != StatusCode::OK && status != StatusCode::CREATED {
                return Err(EnvError::Fetch(format!(
                    "Unexpected HTTP status code {}",
                    status.as_u16()
                )));
            }
            let text = response
                .text()
                .await
                .map_err(|error| EnvError::Fetch(error.to_string()))?;
            serde_json::from_str(&text).map_err(EnvError::from)
        }
        .boxed_env()
    }

    fn get_storage<T>(key: &str) -> TryEnvFuture<Option<T>>
    where
        T: for<'de> Deserialize<'de> + ConditionalSend + 'static,
    {
        let result = storage::read(key).and_then(|bytes| {
            bytes
                .map(|bytes| serde_json::from_slice(&bytes).map_err(EnvError::from))
                .transpose()
        });
        future::ready(result).boxed_env()
    }

    fn set_storage<T: Serialize>(key: &str, value: Option<&T>) -> TryEnvFuture<()> {
        if key == SEARCH_HISTORY_STORAGE_KEY {
            return future::ok(()).boxed_env();
        }
        let key = key.to_owned();
        match value {
            Some(value) => match serde_json::to_vec(value) {
                Ok(bytes) => async move { storage::write(&key, &bytes) }.boxed_env(),
                Err(error) => future::err(EnvError::from(error)).boxed_env(),
            },
            None => async move { storage::remove(&key) }.boxed_env(),
        }
    }

    fn exec_concurrent<F>(future: F)
    where
        F: Future<Output = ()> + ConditionalSend + 'static,
    {
        executor::spawn_concurrent(future);
    }

    fn exec_sequential<F>(future: F)
    where
        F: Future<Output = ()> + ConditionalSend + 'static,
    {
        executor::spawn_sequential(future);
    }

    fn now() -> DateTime<Utc> {
        Utc::now()
    }

    fn flush_analytics() -> EnvFuture<'static, ()> {
        future::ready(()).boxed_env()
    }

    fn analytics_context(
        _ctx: &Ctx,
        _streaming_server: &StreamingServer,
        _path: &str,
    ) -> serde_json::Value {
        serde_json::Value::Null
    }

    #[cfg(debug_assertions)]
    fn log(_message: String) {}
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn storage_round_trips_through_env() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        storage::set_dir(dir.path().to_path_buf()).unwrap();
        executor::RUNTIME.block_on(async {
            AndroidEnv::set_storage("schema_version", Some(&7u32)).await.unwrap();
            assert_eq!(AndroidEnv::get_storage::<u32>("schema_version").await.unwrap(), Some(7));
            AndroidEnv::set_storage::<u32>("schema_version", None).await.unwrap();
            assert_eq!(AndroidEnv::get_storage::<u32>("schema_version").await.unwrap(), None);
        });
    }

    #[test]
    fn search_history_is_never_stored() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        storage::set_dir(dir.path().to_path_buf()).unwrap();
        executor::RUNTIME.block_on(async {
            AndroidEnv::set_storage(SEARCH_HISTORY_STORAGE_KEY, Some(&7u32)).await.unwrap();
            assert_eq!(AndroidEnv::get_storage::<u32>(SEARCH_HISTORY_STORAGE_KEY).await.unwrap(), None);
        });
    }
}
