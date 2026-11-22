use std::path::PathBuf;
use std::time::Duration;

use jni::objects::{JClass, JString};
use jni::refs::Reference;
use jni::strings::JNIString;
use jni::sys::{jlong, jstring};
use jni::{Env, EnvUnowned, jni_str};

use crate::bridge;

fn throw(env: &mut Env, message: String) {
    let _ = env.throw_new(
        jni_str!("java/lang/IllegalStateException"),
        JNIString::from(message),
    );
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_initialize(
    mut env: EnvUnowned,
    _class: JClass,
    data_dir: JString,
) {
    env.with_env(|env| -> jni::errors::Result<()> {
        let data_dir = data_dir.try_to_string(env)?;
        if let Err(message) = bridge::initialize(PathBuf::from(data_dir)) {
            throw(env, message);
        }
        Ok(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_dispatch(
    mut env: EnvUnowned,
    _class: JClass,
    action_json: JString,
    field: JString,
) {
    env.with_env(|env| -> jni::errors::Result<()> {
        let action_json = action_json.try_to_string(env)?;
        let field = if field.is_null() {
            None
        } else {
            Some(field.try_to_string(env)?)
        };
        if let Err(message) = bridge::dispatch(&action_json, field.as_deref()) {
            throw(env, message);
        }
        Ok(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_getState(
    mut env: EnvUnowned,
    _class: JClass,
    field: JString,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let field = field.try_to_string(env)?;
        match bridge::get_state(&field) {
            Ok(json) => Ok(env.new_string(json)?.into_raw()),
            Err(message) => {
                throw(env, message);
                Ok(std::ptr::null_mut())
            }
        }
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_getBoardDelta(
    mut env: EnvUnowned,
    _class: JClass,
    field: JString,
    since: jlong,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let field = field.try_to_string(env)?;
        match bridge::get_board_delta(&field, since.max(0) as u64) {
            Ok(json) => Ok(env.new_string(json)?.into_raw()),
            Err(message) => {
                throw(env, message);
                Ok(std::ptr::null_mut())
            }
        }
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_nextEvent(
    mut env: EnvUnowned,
    _class: JClass,
    timeout_ms: jlong,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        match bridge::next_event(Duration::from_millis(timeout_ms.max(0) as u64)) {
            Some(json) => Ok(env.new_string(json)?.into_raw()),
            None => Ok(std::ptr::null_mut()),
        }
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_shutdown(
    mut env: EnvUnowned,
    _class: JClass,
) {
    env.with_env(|env| -> jni::errors::Result<()> {
        if let Err(message) = bridge::shutdown() {
            throw(env, message);
        }
        Ok(())
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_exportBackup(
    mut env: EnvUnowned,
    _class: JClass,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        match bridge::export_backup() {
            Ok(json) => Ok(env.new_string(json)?.into_raw()),
            Err(message) => {
                throw(env, message);
                Ok(std::ptr::null_mut())
            }
        }
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_terminalis_machtv_core_NativeCore_importBackup(
    mut env: EnvUnowned,
    _class: JClass,
    json: JString,
) -> jstring {
    env.with_env(|env| -> jni::errors::Result<jstring> {
        let json = json.try_to_string(env)?;
        match bridge::import_backup(&json) {
            Ok(outcome) => Ok(env.new_string(outcome)?.into_raw()),
            Err(message) => {
                throw(env, message);
                Ok(std::ptr::null_mut())
            }
        }
    })
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}
