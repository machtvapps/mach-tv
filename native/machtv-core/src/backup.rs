use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use stremio_core::constants::{LIBRARY_RECENT_STORAGE_KEY, LIBRARY_STORAGE_KEY};
use stremio_core::runtime::{Env, EnvError};
use stremio_core::types::library::{LibraryBucket, LibraryBucketRef, LibraryItem};

use crate::env::AndroidEnv;

const FORMAT_VERSION: u32 = 1;
pub const NOT_A_BACKUP: &str = "not a Mach TV backup";

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Backup {
    pub machtv_backup: u32,
    pub created: DateTime<Utc>,
    pub addons: Vec<String>,
    pub library: Vec<LibraryItem>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportOutcome {
    pub restored: usize,
    pub missing_addons: Vec<String>,
}

pub fn backup(addons: Vec<String>, library: &LibraryBucket, created: DateTime<Utc>) -> Backup {
    let mut items: Vec<LibraryItem> = library.items.values().cloned().collect();
    items.sort_by(|a, b| a.id.cmp(&b.id));
    Backup {
        machtv_backup: FORMAT_VERSION,
        created,
        addons,
        library: items,
    }
}

pub fn parse(json: &str) -> Result<Backup, String> {
    let backup: Backup = serde_json::from_str(json).map_err(|_| NOT_A_BACKUP.to_owned())?;
    if backup.machtv_backup != FORMAT_VERSION {
        return Err(NOT_A_BACKUP.to_owned());
    }
    Ok(backup)
}

pub fn merge(library: &mut LibraryBucket, incoming: Vec<LibraryItem>) -> usize {
    let mut restored = 0;
    for item in incoming {
        let newer = library
            .items
            .get(&item.id)
            .is_none_or(|existing| item.state.last_watched > existing.state.last_watched);
        if newer {
            library.items.insert(item.id.clone(), item);
            restored += 1;
        }
    }
    restored
}

pub async fn store(library: &LibraryBucket) -> Result<(), EnvError> {
    let (recent, other) = library.split_items_by_recent();
    AndroidEnv::set_storage(
        LIBRARY_RECENT_STORAGE_KEY,
        Some(&LibraryBucketRef::new(&library.uid, &recent)),
    )
    .await?;
    AndroidEnv::set_storage(
        LIBRARY_STORAGE_KEY,
        Some(&LibraryBucketRef::new(&library.uid, &other)),
    )
    .await
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;

    pub(crate) fn item(id: &str, last_watched: Option<&str>) -> LibraryItem {
        serde_json::from_value(serde_json::json!({
            "_id": id,
            "name": id,
            "type": "movie",
            "poster": null,
            "posterShape": "poster",
            "removed": true,
            "temp": true,
            "_ctime": "2026-11-12T16:42:38Z",
            "_mtime": "2026-11-12T19:31:05Z",
            "state": {
                "lastWatched": last_watched,
                "timeWatched": 0,
                "timeOffset": 24000,
                "overallTimeWatched": 0,
                "timesWatched": 1,
                "flaggedWatched": 0,
                "duration": 634533,
                "video_id": id,
                "watched": null,
                "noNotif": false
            },
            "behaviorHints": {"defaultVideoId": id, "featuredVideoId": null, "hasScheduledVideos": false}
        }))
        .unwrap()
    }

    #[test]
    fn merge_adds_new_items_and_keeps_the_later_watch() {
        let mut library = LibraryBucket::new(None, vec![
            item("old", Some("2026-11-01T00:00:00Z")),
            item("new", Some("2026-11-10T00:00:00Z")),
            item("never", None),
        ]);
        let restored = merge(&mut library, vec![
            item("old", Some("2026-11-05T00:00:00Z")),
            item("new", Some("2026-11-02T00:00:00Z")),
            item("never", None),
            item("added", None),
        ]);
        assert_eq!(restored, 2);
        assert_eq!(library.items["old"].state.last_watched.unwrap().to_rfc3339(), "2026-11-05T00:00:00+00:00");
        assert_eq!(library.items["new"].state.last_watched.unwrap().to_rfc3339(), "2026-11-10T00:00:00+00:00");
        assert!(library.items.contains_key("added"));
    }

    #[test]
    fn only_version_one_backups_parse() {
        assert_eq!(parse("{}").err().unwrap(), NOT_A_BACKUP);
        assert_eq!(parse("not json").err().unwrap(), NOT_A_BACKUP);
        let two = r#"{"machtvBackup":2,"created":"2026-11-13T00:00:00Z","addons":[],"library":[]}"#;
        assert_eq!(parse(two).err().unwrap(), NOT_A_BACKUP);
        let one = r#"{"machtvBackup":1,"created":"2026-11-13T00:00:00Z","addons":[],"library":[]}"#;
        assert!(parse(one).is_ok());
    }
}
