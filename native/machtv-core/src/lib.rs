mod action_json;
mod backup;
mod bridge;
mod env;
mod executor;
mod jni_api;
mod model;
mod seed;
mod storage;
mod type_board;

#[cfg(test)]
static GLOBAL_TEST_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

#[cfg(test)]
pub(crate) fn lock_globals() -> std::sync::MutexGuard<'static, ()> {
    GLOBAL_TEST_LOCK
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}
