use stremio_core::Model;
use stremio_core::models::addon_details::AddonDetails;
use stremio_core::models::catalog_with_filters::CatalogWithFilters;
use stremio_core::models::catalogs_with_extra::CatalogsWithExtra;
use stremio_core::models::common::Loadable;
use stremio_core::models::continue_watching_preview::ContinueWatchingPreview;
use stremio_core::models::ctx::Ctx;
use stremio_core::models::library_with_filters::{LibraryWithFilters, NotRemovedFilter, Sort};
use stremio_core::models::local_search::LocalSearch;
use stremio_core::models::meta_details::MetaDetails;
use stremio_core::models::player::Player;
use stremio_core::types::library::LibraryBucket;
use stremio_core::types::resource::MetaItemPreview;

use crate::env::AndroidEnv;
use crate::type_board::TypeBoard;

#[derive(Model, Clone)]
#[model(AndroidEnv)]
pub struct MachTvModel {
    pub ctx: Ctx,
    pub home: CatalogsWithExtra,
    pub addon_details: AddonDetails,
    pub meta_details: MetaDetails,
    pub player: Player,
    pub continue_watching_preview: ContinueWatchingPreview,
    pub movies: TypeBoard,
    pub series: TypeBoard,
    pub catalog: CatalogWithFilters<MetaItemPreview>,
    pub search: CatalogsWithExtra,
    pub local_search: LocalSearch,
    pub library: LibraryWithFilters<NotRemovedFilter>,
}

impl MachTvModel {
    pub fn new(ctx: Ctx) -> Self {
        let (continue_watching_preview, _) = ContinueWatchingPreview::new(&ctx.library, &ctx.notifications);
        let (catalog, _) = CatalogWithFilters::<MetaItemPreview>::new(&ctx.profile);
        let (local_search, _) = LocalSearch::new::<AndroidEnv>();
        let (library, _) = LibraryWithFilters::<NotRemovedFilter>::new(&ctx.library, &ctx.notifications);
        MachTvModel {
            ctx,
            home: Default::default(),
            addon_details: Default::default(),
            meta_details: Default::default(),
            player: Default::default(),
            continue_watching_preview,
            movies: Default::default(),
            series: Default::default(),
            catalog,
            search: Default::default(),
            local_search,
            library,
        }
    }

    pub fn state_json(&self, field: &MachTvModelField) -> Result<String, serde_json::Error> {
        match field {
            MachTvModelField::Ctx => {
                let mut state = serde_json::to_value(&self.ctx)?;
                state["watched"] = serde_json::json!(watched_items(&self.ctx.library));
                state["library"] = serde_json::json!(library_items(&self.ctx.library));
                serde_json::to_string(&state)
            }
            MachTvModelField::Home => serde_json::to_string(&self.home),
            MachTvModelField::AddonDetails => serde_json::to_string(&self.addon_details),
            MachTvModelField::MetaDetails => {
                let mut state = serde_json::to_value(&self.meta_details)?;
                state["watchedVideos"] = serde_json::json!(watched_videos(&self.meta_details));
                serde_json::to_string(&state)
            }
            MachTvModelField::Player => serde_json::to_string(&self.player),
            MachTvModelField::ContinueWatchingPreview => serde_json::to_string(&self.continue_watching_preview),
            MachTvModelField::Movies => serde_json::to_string(&self.movies),
            MachTvModelField::Series => serde_json::to_string(&self.series),
            MachTvModelField::Catalog => serde_json::to_string(&self.catalog),
            MachTvModelField::Search => serde_json::to_string(&self.search),
            MachTvModelField::LocalSearch => serde_json::to_string(&serde_json::json!({
                "searchResults": &self.local_search.search_results,
                "ready": matches!(self.local_search.latest_records, Some(Loadable::Ready(_))),
            })),
            MachTvModelField::Library => serde_json::to_string(&self.library),
        }
    }

    pub fn board_delta_json(&self, field: &MachTvModelField, since: u64) -> Result<String, String> {
        let board = match field {
            MachTvModelField::Movies => &self.movies,
            MachTvModelField::Series => &self.series,
            _ => return Err("only movies and series send board deltas".to_owned()),
        };
        serde_json::to_string(&board.delta(since)).map_err(|error| error.to_string())
    }
}

fn watched_items(library: &LibraryBucket) -> Vec<&str> {
    library
        .items
        .values()
        .filter(|item| item.state.times_watched > 0 || item.state.flagged_watched > 0)
        .map(|item| item.id.as_str())
        .collect()
}

fn library_items(library: &LibraryBucket) -> Vec<serde_json::Value> {
    let mut items: Vec<_> = library.items.values().filter(|item| !item.removed).collect();
    items.sort_by(|a, b| Sort::LastWatched.sort_items(a, b));
    items
        .into_iter()
        .map(|item| serde_json::json!({"_id": item.id, "type": item.r#type, "name": item.name, "poster": item.poster}))
        .collect()
}

fn watched_videos(meta_details: &MetaDetails) -> Vec<&str> {
    let Some(watched) = &meta_details.watched else {
        return Vec::new();
    };
    meta_details
        .meta_items
        .iter()
        .find_map(|item| match &item.content {
            Some(Loadable::Ready(meta)) => Some(meta),
            _ => None,
        })
        .map(|meta| {
            meta.videos
                .iter()
                .filter(|video| watched.get_video(&video.id))
                .map(|video| video.id.as_str())
                .collect()
        })
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;
    use stremio_core::models::common::ResourceLoadable;
    use stremio_core::types::addon::{ResourcePath, ResourceRequest};
    use stremio_core::types::watched_bitfield::WatchedBitField;

    #[test]
    fn library_items_lists_kept_titles_latest_watched_first() {
        let kept = |id: &str, last_watched: &str| {
            let mut item = crate::backup::tests::item(id, Some(last_watched));
            item.removed = false;
            item
        };
        let library = LibraryBucket::new(
            None,
            vec![
                kept("tt1254207", "2026-11-10T10:00:00Z"),
                kept("tt1748166", "2026-11-12T10:00:00Z"),
                crate::backup::tests::item("tt0807840", Some("2026-11-13T10:00:00Z")),
            ],
        );
        let items = library_items(&library);
        assert_eq!(items.iter().map(|item| item["_id"].as_str().unwrap()).collect::<Vec<_>>(), ["tt1748166", "tt1254207"]);
        assert_eq!(items[0], serde_json::json!({"_id": "tt1748166", "type": "movie", "name": "tt1748166", "poster": null}));
    }

    #[test]
    fn watched_videos_lists_marked_episodes() {
        let pilot = "tt1748166:1:1".to_owned();
        let second = "tt1748166:1:2".to_owned();
        let meta = serde_json::from_value(serde_json::json!({
            "id": "tt1748166",
            "type": "series",
            "name": "Pioneer One",
            "videos": [{"id": &pilot}, {"id": &second}]
        }))
        .unwrap();
        let meta_details = MetaDetails {
            meta_items: vec![ResourceLoadable {
                request: ResourceRequest::new(
                    "https://v3-cinemeta.strem.io/manifest.json".parse().unwrap(),
                    ResourcePath::without_extra("meta", "series", "tt1748166"),
                ),
                content: Some(Loadable::Ready(meta)),
            }],
            watched: Some(WatchedBitField::construct_from_array(
                vec![true, false],
                vec![pilot.clone(), second],
            )),
            ..Default::default()
        };
        assert_eq!(watched_videos(&meta_details), [pilot.as_str()]);
    }
}
