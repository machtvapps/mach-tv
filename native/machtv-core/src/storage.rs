use std::fs;
use std::io::{ErrorKind, Write};
use std::path::{Path, PathBuf};
use std::sync::RwLock;
use std::sync::atomic::{AtomicU64, Ordering};

use stremio_core::runtime::EnvError;

static STORAGE_DIR: RwLock<Option<PathBuf>> = RwLock::new(None);
static WRITE_SEQUENCE: AtomicU64 = AtomicU64::new(0);

pub fn set_dir(dir: PathBuf) -> Result<(), EnvError> {
    fs::create_dir_all(&dir).map_err(|error| EnvError::StorageWriteError(error.to_string()))?;
    *STORAGE_DIR
        .write()
        .unwrap_or_else(|poisoned| poisoned.into_inner()) = Some(dir);
    Ok(())
}

pub fn read(key: &str) -> Result<Option<Vec<u8>>, EnvError> {
    read_in(&dir()?, key)
}

pub fn write(key: &str, bytes: &[u8]) -> Result<(), EnvError> {
    write_in(&dir()?, key, bytes)
}

pub fn remove(key: &str) -> Result<(), EnvError> {
    remove_in(&dir()?, key)
}

fn dir() -> Result<PathBuf, EnvError> {
    STORAGE_DIR
        .read()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .clone()
        .ok_or(EnvError::StorageUnavailable)
}

fn key_path(dir: &Path, key: &str) -> PathBuf {
    dir.join(format!("{key}.json"))
}

fn read_in(dir: &Path, key: &str) -> Result<Option<Vec<u8>>, EnvError> {
    match fs::read(key_path(dir, key)) {
        Ok(bytes) => Ok(Some(bytes)),
        Err(error) if error.kind() == ErrorKind::NotFound => Ok(None),
        Err(error) => Err(EnvError::StorageReadError(error.to_string())),
    }
}

fn write_in(dir: &Path, key: &str, bytes: &[u8]) -> Result<(), EnvError> {
    let sequence = WRITE_SEQUENCE.fetch_add(1, Ordering::Relaxed);
    let temporary = dir.join(format!("{key}.json.{sequence}.tmp"));
    let result = (|| {
        let mut file = fs::File::create(&temporary)?;
        file.write_all(bytes)?;
        file.sync_all()?;
        fs::rename(&temporary, key_path(dir, key))
    })();
    result.map_err(|error| EnvError::StorageWriteError(error.to_string()))
}

fn remove_in(dir: &Path, key: &str) -> Result<(), EnvError> {
    match fs::remove_file(key_path(dir, key)) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == ErrorKind::NotFound => Ok(()),
        Err(error) => Err(EnvError::StorageWriteError(error.to_string())),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn missing_key_reads_as_none() {
        let dir = tempdir().unwrap();
        assert_eq!(read_in(dir.path(), "profile").unwrap(), None);
    }

    #[test]
    fn written_value_reads_back() {
        let dir = tempdir().unwrap();
        write_in(dir.path(), "profile", b"{\"a\":1}").unwrap();
        assert_eq!(read_in(dir.path(), "profile").unwrap(), Some(b"{\"a\":1}".to_vec()));
    }

    #[test]
    fn second_write_replaces_first() {
        let dir = tempdir().unwrap();
        write_in(dir.path(), "profile", b"1").unwrap();
        write_in(dir.path(), "profile", b"2").unwrap();
        assert_eq!(read_in(dir.path(), "profile").unwrap(), Some(b"2".to_vec()));
    }

    #[test]
    fn interrupted_write_leaves_previous_value_intact() {
        let dir = tempdir().unwrap();
        write_in(dir.path(), "profile", b"old").unwrap();
        std::fs::write(dir.path().join("profile.json.999.tmp"), b"partial").unwrap();
        assert_eq!(read_in(dir.path(), "profile").unwrap(), Some(b"old".to_vec()));
        write_in(dir.path(), "profile", b"new").unwrap();
        assert_eq!(read_in(dir.path(), "profile").unwrap(), Some(b"new".to_vec()));
    }

    #[test]
    fn remove_deletes_the_key_and_tolerates_absence() {
        let dir = tempdir().unwrap();
        write_in(dir.path(), "profile", b"1").unwrap();
        remove_in(dir.path(), "profile").unwrap();
        assert_eq!(read_in(dir.path(), "profile").unwrap(), None);
        remove_in(dir.path(), "profile").unwrap();
    }
}
