use std::path::PathBuf;
use std::sync::mpsc::{self, Receiver};
use std::sync::{Mutex, MutexGuard};
use std::time::Duration;

use futures::{StreamExt, try_join};
use stremio_core::constants::{
    DISMISSED_EVENTS_STORAGE_KEY, LIBRARY_RECENT_STORAGE_KEY, LIBRARY_STORAGE_KEY,
    NOTIFICATIONS_STORAGE_KEY, PROFILE_STORAGE_KEY, SEARCH_HISTORY_STORAGE_KEY,
    STREAMING_SERVER_URLS_STORAGE_KEY, STREAMS_STORAGE_KEY,
};
use stremio_core::models::ctx::Ctx;
use stremio_core::runtime::msg::Action;
use stremio_core::runtime::{Env, EnvError, Runtime, RuntimeAction};
use stremio_core::types::events::DismissedEventsBucket;
use stremio_core::types::library::LibraryBucket;
use stremio_core::types::notifications::NotificationsBucket;
use stremio_core::types::profile::Profile;
use stremio_core::types::search_history::SearchHistoryBucket;
use stremio_core::types::server_urls::ServerUrlsBucket;
use stremio_core::types::streams::StreamsBucket;

use crate::env::AndroidEnv;
use crate::model::{MachTvModel, MachTvModelField};
use crate::{backup, executor, seed, storage};

type CoreRuntime = Runtime<AndroidEnv, MachTvModel>;

const ENGINE_URL: &str = "http://127.0.0.1:11479/";

const EVENT_BUFFER: usize = 1000;
const DRAIN_TIMEOUT: Duration = Duration::from_secs(10);

static RUNNING: Mutex<Option<CoreRuntime>> = Mutex::new(None);
static EVENTS: Mutex<Option<Receiver<String>>> = Mutex::new(None);

pub fn initialize(data_dir: PathBuf) -> Result<(), String> {
    if running().is_some() {
        return Err("core is already running".to_owned());
    }
    storage::set_dir(data_dir.join("core")).map_err(describe)?;
    start()
}

fn start() -> Result<(), String> {
    let mut running = running();
    if running.is_some() {
        return Err("core is already running".to_owned());
    }
    let ctx = executor::RUNTIME.block_on(load_ctx()).map_err(describe)?;
    let (runtime, mut runtime_events) =
        CoreRuntime::new(MachTvModel::new(ctx), vec![], EVENT_BUFFER);
    let (sender, receiver) = mpsc::channel();
    executor::spawn_concurrent(async move {
        while let Some(event) = runtime_events.next().await {
            let json = serde_json::to_string(&event).expect("runtime event serializes");
            let _ = sender.send(json);
        }
    });
    *events() = Some(receiver);
    *running = Some(runtime);
    Ok(())
}

pub fn export_backup() -> Result<String, String> {
    let runtime = running().clone().ok_or_else(not_running)?;
    let model = runtime.model().map_err(|_| "model lock poisoned".to_owned())?;
    let addons = model.ctx.profile.addons.iter().map(|addon| addon.transport_url.to_string()).collect();
    serde_json::to_string(&backup::backup(addons, &model.ctx.library, AndroidEnv::now())).map_err(|error| error.to_string())
}

pub fn import_backup(json: &str) -> Result<String, String> {
    let incoming = backup::parse(json)?;
    let (library, restored, missing_addons) = {
        let runtime = running().clone().ok_or_else(not_running)?;
        let model = runtime.model().map_err(|_| "model lock poisoned".to_owned())?;
        let mut library = model.ctx.library.clone();
        let restored = backup::merge(&mut library, incoming.library);
        let installed: Vec<String> = model.ctx.profile.addons.iter().map(|addon| addon.transport_url.to_string()).collect();
        let missing: Vec<String> = incoming.addons.into_iter().filter(|url| !installed.contains(url)).collect();
        (library, restored, missing)
    };
    shutdown()?;
    executor::RUNTIME.block_on(backup::store(&library)).map_err(describe)?;
    start()?;
    serde_json::to_string(&backup::ImportOutcome { restored, missing_addons }).map_err(|error| error.to_string())
}

pub fn dispatch(action_json: &str, field: Option<&str>) -> Result<(), String> {
    let action: Action =
        serde_json::from_str(action_json).map_err(|error| format!("invalid action: {error}"))?;
    let field = field.map(parse_field).transpose()?;
    let runtime = running().clone().ok_or_else(not_running)?;
    runtime.dispatch(RuntimeAction { field, action });
    Ok(())
}

pub fn get_state(field: &str) -> Result<String, String> {
    let field = parse_field(field)?;
    let runtime = running().clone().ok_or_else(not_running)?;
    let model = runtime
        .model()
        .map_err(|_| "model lock poisoned".to_owned())?;
    model.state_json(&field).map_err(|error| error.to_string())
}

pub fn get_board_delta(field: &str, since: u64) -> Result<String, String> {
    let field = parse_field(field)?;
    let runtime = running().clone().ok_or_else(not_running)?;
    let model = runtime
        .model()
        .map_err(|_| "model lock poisoned".to_owned())?;
    model.board_delta_json(&field, since)
}

pub fn next_event(timeout: Duration) -> Option<String> {
    events().as_ref()?.recv_timeout(timeout).ok()
}

pub fn shutdown() -> Result<(), String> {
    if running().take().is_none() {
        return Err(not_running());
    }
    *events() = None;
    executor::drain_sequential(DRAIN_TIMEOUT)
}

async fn load_ctx() -> Result<Ctx, EnvError> {
    AndroidEnv::migrate_storage_schema().await?;
    let (
        profile,
        recent_bucket,
        other_bucket,
        streams_bucket,
        server_urls_bucket,
        notifications_bucket,
        search_history_bucket,
        dismissed_events_bucket,
    ) = try_join!(
        AndroidEnv::get_storage::<Profile>(PROFILE_STORAGE_KEY),
        AndroidEnv::get_storage::<LibraryBucket>(LIBRARY_RECENT_STORAGE_KEY),
        AndroidEnv::get_storage::<LibraryBucket>(LIBRARY_STORAGE_KEY),
        AndroidEnv::get_storage::<StreamsBucket>(STREAMS_STORAGE_KEY),
        AndroidEnv::get_storage::<ServerUrlsBucket>(STREAMING_SERVER_URLS_STORAGE_KEY),
        AndroidEnv::get_storage::<NotificationsBucket>(NOTIFICATIONS_STORAGE_KEY),
        AndroidEnv::get_storage::<SearchHistoryBucket>(SEARCH_HISTORY_STORAGE_KEY),
        AndroidEnv::get_storage::<DismissedEventsBucket>(DISMISSED_EVENTS_STORAGE_KEY),
    )?;
    let mut profile = match profile {
        Some(profile) => profile,
        None => {
            let profile = seed::seeded_profile();
            AndroidEnv::set_storage(PROFILE_STORAGE_KEY, Some(&profile)).await?;
            profile
        }
    };
    profile.settings.streaming_server_url = ENGINE_URL.parse().expect("engine url");
    let mut library = LibraryBucket::new(profile.uid(), vec![]);
    if let Some(recent_bucket) = recent_bucket {
        library.merge_bucket(recent_bucket);
    }
    if let Some(other_bucket) = other_bucket {
        library.merge_bucket(other_bucket);
    }
    let streams = streams_bucket.unwrap_or_else(|| StreamsBucket::new(profile.uid()));
    let server_urls = server_urls_bucket
        .unwrap_or_else(|| ServerUrlsBucket::new::<AndroidEnv>(profile.uid()));
    let notifications = notifications_bucket
        .unwrap_or_else(|| NotificationsBucket::new::<AndroidEnv>(profile.uid(), vec![]));
    let search_history =
        search_history_bucket.unwrap_or_else(|| SearchHistoryBucket::new(profile.uid()));
    let dismissed_events =
        dismissed_events_bucket.unwrap_or_else(|| DismissedEventsBucket::new(profile.uid()));
    Ok(Ctx::new(
        profile,
        library,
        streams,
        server_urls,
        notifications,
        search_history,
        dismissed_events,
    ))
}

fn parse_field(field: &str) -> Result<MachTvModelField, String> {
    serde_json::from_value(serde_json::Value::String(field.to_owned()))
        .map_err(|_| format!("unknown model field: {field}"))
}

fn running() -> MutexGuard<'static, Option<CoreRuntime>> {
    RUNNING.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn events() -> MutexGuard<'static, Option<Receiver<String>>> {
    EVENTS.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn not_running() -> String {
    "core is not running".to_owned()
}

fn describe(error: EnvError) -> String {
    error.message()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use tempfile::tempdir;

    fn addon_ids(ctx_json: &str) -> Vec<String> {
        let ctx: serde_json::Value = serde_json::from_str(ctx_json).unwrap();
        ctx["profile"]["addons"]
            .as_array()
            .unwrap()
            .iter()
            .map(|addon| addon["manifest"]["id"].as_str().unwrap().to_owned())
            .collect()
    }

    #[test]
    fn first_initialize_seeds_cinemeta_and_opensubtitles_v3() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let ids = addon_ids(&get_state("ctx").unwrap());
        shutdown().unwrap();
        assert_eq!(ids, ["com.linvo.cinemeta", "org.stremio.opensubtitlesv3"]);
        assert!(dir.path().join("core").join("profile.json").exists());
    }

    #[test]
    fn stored_profile_is_not_reseeded() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        shutdown().unwrap();
        let path = dir.path().join("core").join("profile.json");
        let mut profile: serde_json::Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
        let cinemeta = profile["addons"][0].clone();
        profile["addons"] = serde_json::json!([cinemeta]);
        fs::write(&path, serde_json::to_vec(&profile).unwrap()).unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let ids = addon_ids(&get_state("ctx").unwrap());
        shutdown().unwrap();
        assert_eq!(ids, ["com.linvo.cinemeta"]);
    }

    #[test]
    fn initialize_while_running_fails() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let second = initialize(dir.path().to_path_buf());
        shutdown().unwrap();
        assert!(second.is_err());
    }

    #[test]
    fn malformed_action_and_unknown_field_are_rejected() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let bad_action = dispatch(r#"{"action":"Nope"}"#, None);
        let bad_field = get_state("nope");
        shutdown().unwrap();
        assert!(bad_action.is_err());
        assert!(bad_field.is_err());
    }

    #[test]
    fn board_delta_serves_movies_and_series_only() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let movies: serde_json::Value = serde_json::from_str(&get_board_delta("movies", 0).unwrap()).unwrap();
        let series = get_board_delta("series", 0);
        let home = get_board_delta("home", 0);
        shutdown().unwrap();
        assert_eq!(movies["count"], 0);
        assert_eq!(movies["rows"], serde_json::json!([]));
        assert!(series.is_ok());
        assert!(home.is_err());
    }

    #[test]
    fn loading_home_emits_new_state_for_home() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        dispatch(
            r#"{"action":"Load","args":{"model":"CatalogsWithExtra","args":{"type":"movie","extra":[]}}}"#,
            Some("home"),
        )
        .unwrap();
        let mut saw_home = false;
        while let Some(event) = next_event(Duration::from_secs(5)) {
            let event: serde_json::Value = serde_json::from_str(&event).unwrap();
            if event["name"] == "NewState"
                && event["args"].as_array().unwrap().iter().any(|field| field == "home")
            {
                saw_home = true;
                break;
            }
        }
        shutdown().unwrap();
        assert!(saw_home);
    }

    #[test]
    fn player_converts_torrents_against_the_local_engine() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let dispatched = dispatch(
            r#"{"action":"Load","args":{"model":"Player","args":{"stream":{"infoHash":"88594aaacbde40ef3e2510c47374ec0aa396c08e","fileIdx":0}}}}"#,
            Some("player"),
        );
        let player = get_state("player");
        shutdown().unwrap();
        dispatched.unwrap();
        let player: serde_json::Value = serde_json::from_str(&player.unwrap()).unwrap();
        assert_eq!(player["stream"]["type"], "Ready");
        let url = player["stream"]["content"][0]["streaming_url"].as_str().unwrap();
        assert!(
            url.starts_with("http://127.0.0.1:11479/88594aaacbde40ef3e2510c47374ec0aa396c08e/0"),
            "{url}"
        );
    }

    #[test]
    fn continue_watching_preview_starts_empty() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let state = get_state("continue_watching_preview");
        shutdown().unwrap();
        let state: serde_json::Value = serde_json::from_str(&state.unwrap()).unwrap();
        assert_eq!(state["items"], serde_json::json!([]));
    }

    #[test]
    fn import_restores_items_and_lists_missing_addons() {
        let _guard = crate::lock_globals();
        let dir = tempdir().unwrap();
        initialize(dir.path().to_path_buf()).unwrap();
        let file = serde_json::json!({
            "machtvBackup": 1,
            "created": "2026-11-13T00:00:00Z",
            "addons": ["https://v3-cinemeta.strem.io/manifest.json", "https://torrentio.strem.fun/sort=seeders/manifest.json"],
            "library": [crate::backup::tests::item("tt1254207", Some("2026-11-12T19:31:04Z"))]
        });
        let outcome: serde_json::Value = serde_json::from_str(&import_backup(&file.to_string()).unwrap()).unwrap();
        let exported: serde_json::Value = serde_json::from_str(&export_backup().unwrap()).unwrap();
        let again: serde_json::Value = serde_json::from_str(&import_backup(&file.to_string()).unwrap()).unwrap();
        shutdown().unwrap();
        assert_eq!(outcome["restored"], 1);
        assert_eq!(outcome["missingAddons"], serde_json::json!(["https://torrentio.strem.fun/sort=seeders/manifest.json"]));
        assert_eq!(exported["library"][0]["_id"], "tt1254207");
        assert_eq!(exported["library"][0]["state"]["timeOffset"], 24000);
        assert_eq!(again["restored"], 0);
        let stored = fs::read_to_string(dir.path().join("core").join("library_recent.json")).unwrap();
        assert!(stored.contains("tt1254207"));
    }
}
