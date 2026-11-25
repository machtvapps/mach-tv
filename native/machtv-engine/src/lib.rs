use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Mutex;

use jni::objects::{JClass, JObject, JString};
use jni::strings::JNIString;
use jni::sys::jstring;
use jni::{Env, EnvUnowned, jni_str};
use stream_server::{ServerConfig, ServerHandle};

static SERVER: Mutex<Option<ServerHandle>> = Mutex::new(None);

fn throw(env: &mut Env, message: String) {
    let _ = env.throw_new(
        jni_str!("java/lang/IllegalStateException"),
        JNIString::from(message),
    );
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_machtvapps_machtv_core_NativeEngine_start(
    mut env: EnvUnowned,
    _class: JClass,
    context: JObject,
    config_dir: JString,
    cache_dir: JString,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let mut server = SERVER.lock().unwrap();
        if server.is_some() {
            throw(env, "engine is already running".to_owned());
            return Ok(std::ptr::null_mut());
        }
        rustls_platform_verifier::android::init_with_env(env, context)?;
        let mut config = ServerConfig::embedded();
        config.config_dir = Some(PathBuf::from(config_dir.try_to_string(env)?));
        config.cache_dir = Some(PathBuf::from(cache_dir.try_to_string(env)?));
        config.http_addr = SocketAddr::from(([127, 0, 0, 1], 11479));
        match stream_server::start(config) {
            Ok(handle) => {
                let url = format!("http://{}", handle.bound_http_addr());
                *server = Some(handle);
                Ok(env.new_string(url)?.into_raw())
            }
            Err(error) => {
                throw(env, format!("engine failed to start: {error:#}"));
                Ok(std::ptr::null_mut())
            }
        }
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_machtvapps_machtv_core_NativeEngine_stop(
    mut env: EnvUnowned,
    _class: JClass,
) {
    env.with_env(|env| -> jni::errors::Result<()> {
        if let Some(handle) = SERVER.lock().unwrap().take() {
            let _ = handle.shutdown();
            if let Err(error) = handle.join() {
                throw(env, format!("engine failed to stop: {error:#}"));
            }
        }
        Ok(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}
