use std::ops::Range;

use chrono::Datelike;
use futures::FutureExt;
use serde::Serialize;
use stremio_core::constants::{CATALOG_RESOURCE_NAME, CINEMETA_URL};
use stremio_core::models::catalogs_with_extra::{Catalog, Selected};
use stremio_core::models::common::{
    Loadable, ResourceAction, ResourceLoadable, eq_update, resource_update_with_vector_content,
};
use stremio_core::models::ctx::Ctx;
use stremio_core::runtime::msg::{Action, ActionCatalogsWithExtra, ActionLoad, Internal, Msg};
use stremio_core::runtime::{Effect, EffectFuture, Effects, Env, EnvFutureExt, UpdateWithCtx};
use stremio_core::types::addon::{AggrRequest, ExtraValue, ResourcePath, ResourceRequest};
use stremio_core::types::profile::Profile;
use stremio_core::types::resource::MetaItemPreview;
use url::Url;

const NEW_CATALOG_ID: &str = "year";
const GENRE_CATALOG_ID: &str = "top";
const GENRE_EXTRA_NAME: &str = "genre";

#[derive(Default, Clone, Serialize, Debug)]
pub struct TypeBoard {
    pub selected: Option<Selected>,
    pub catalogs: Vec<Catalog<MetaItemPreview>>,
    #[serde(skip)]
    version: u64,
    #[serde(skip)]
    row_versions: Vec<u64>,
}

#[derive(Serialize)]
pub struct BoardDelta<'a> {
    version: u64,
    selected: &'a Option<Selected>,
    count: usize,
    rows: Vec<DeltaRow<'a>>,
}

#[derive(Serialize)]
struct DeltaRow<'a> {
    index: usize,
    catalog: &'a Catalog<MetaItemPreview>,
}

impl TypeBoard {
    pub fn delta(&self, since: u64) -> BoardDelta<'_> {
        BoardDelta {
            version: self.version,
            selected: &self.selected,
            count: self.catalogs.len(),
            rows: self
                .catalogs
                .iter()
                .enumerate()
                .filter(|(index, _)| self.row_versions.get(*index).is_none_or(|version| *version > since))
                .map(|(index, catalog)| DeltaRow { index, catalog })
                .collect(),
        }
    }

    fn touch_all(&mut self) {
        self.version += 1;
        self.row_versions = vec![self.version; self.catalogs.len()];
    }

    fn touch(&mut self, index: usize) {
        self.version += 1;
        if let Some(version) = self.row_versions.get_mut(index) {
            *version = self.version;
        }
    }
}

impl<E: Env + 'static> UpdateWithCtx<E> for TypeBoard {
    fn update(&mut self, msg: &Msg, ctx: &Ctx) -> Effects {
        match msg {
            Msg::Action(Action::Load(ActionLoad::CatalogsWithExtra(selected))) => {
                let selected_effects = eq_update(&mut self.selected, Some(selected.to_owned()));
                let catalogs_effects =
                    catalogs_update::<E>(&mut self.catalogs, &self.selected, &ctx.profile);
                let effects = selected_effects.join(catalogs_effects);
                if effects.has_changed {
                    self.touch_all();
                }
                effects
            }
            Msg::Action(Action::Unload) => {
                let effects = eq_update(&mut self.selected, None).join(eq_update(&mut self.catalogs, vec![]));
                if effects.has_changed {
                    self.touch_all();
                }
                effects
            }
            Msg::Action(Action::CatalogsWithExtra(ActionCatalogsWithExtra::LoadRange(range))) => {
                let marked = mark_range_loading(&mut self.catalogs, range);
                if marked.is_empty() {
                    return Effects::none().unchanged();
                }
                marked.iter().for_each(|(index, _)| self.touch(*index));
                Effects::many(marked.into_iter().map(|(_, request)| load::<E>(request)).collect())
            }
            Msg::Internal(Internal::ResourceRequestResult(request, result)) => {
                let Some(index) = self
                    .catalogs
                    .iter()
                    .position(|catalog| catalog.last().is_some_and(|page| page.request == *request))
                else {
                    return Effects::none().unchanged();
                };
                let page = self.catalogs[index].last_mut().expect("found row has a page");
                let effects = resource_update_with_vector_content::<E, _>(
                    page,
                    ResourceAction::ResourceRequestResult { request, result },
                );
                if effects.has_changed {
                    self.touch(index);
                }
                effects
            }
            Msg::Internal(Internal::ProfileChanged) => {
                let effects = catalogs_update::<E>(&mut self.catalogs, &self.selected, &ctx.profile);
                if effects.has_changed {
                    self.touch_all();
                }
                effects
            }
            _ => Effects::none().unchanged(),
        }
    }
}

pub fn plan(selected: &Option<Selected>, profile: &Profile, year: i32) -> Vec<ResourceRequest> {
    let Some(Selected { r#type: Some(r#type), .. }) = selected else {
        return vec![];
    };
    let no_extra = vec![];
    let type_filter = Some(r#type.to_owned());
    let mut requests: Vec<ResourceRequest> = AggrRequest::AllCatalogs {
        extra: &no_extra,
        r#type: &type_filter,
    }
    .plan(&profile.addons)
    .into_iter()
    .map(|(_, request)| request)
    .collect();
    let Some(cinemeta) = profile
        .addons
        .iter()
        .find(|addon| addon.transport_url == *CINEMETA_URL)
    else {
        return requests;
    };
    let catalog = |id: &str| {
        cinemeta
            .manifest
            .catalogs
            .iter()
            .find(|catalog| catalog.id == id && catalog.r#type == *r#type)
    };
    if catalog(NEW_CATALOG_ID).is_some() {
        requests.push(genre_request(
            &cinemeta.transport_url,
            r#type,
            NEW_CATALOG_ID,
            &year.to_string(),
        ));
    }
    if let Some(top) = catalog(GENRE_CATALOG_ID) {
        let genres: Vec<String> = top
            .extra
            .iter()
            .filter(|prop| prop.name == GENRE_EXTRA_NAME)
            .flat_map(|prop| prop.options.clone())
            .collect();
        for genre in genres {
            requests.push(genre_request(
                &cinemeta.transport_url,
                r#type,
                GENRE_CATALOG_ID,
                &genre,
            ));
        }
    }
    requests
}

fn genre_request(base: &Url, r#type: &str, id: &str, genre: &str) -> ResourceRequest {
    ResourceRequest::new(
        base.to_owned(),
        ResourcePath::with_extra(
            CATALOG_RESOURCE_NAME,
            r#type,
            id,
            &[ExtraValue {
                name: GENRE_EXTRA_NAME.to_owned(),
                value: genre.to_owned(),
            }],
        ),
    )
}

fn catalogs_update<E: Env + 'static>(
    catalogs: &mut Vec<Catalog<MetaItemPreview>>,
    selected: &Option<Selected>,
    profile: &Profile,
) -> Effects {
    let next_catalogs = plan(selected, profile, E::now().year())
        .into_iter()
        .map(|request| {
            catalogs
                .iter()
                .find(|catalog| {
                    matches!(catalog.first(), Some(page) if page.request == request && page.content.is_some())
                })
                .map(|catalog| catalog.to_owned())
                .unwrap_or_else(|| {
                    vec![ResourceLoadable {
                        request,
                        content: None,
                    }]
                })
        })
        .collect();
    eq_update(catalogs, next_catalogs)
}

fn mark_range_loading(
    catalogs: &mut [Catalog<MetaItemPreview>],
    range: &Range<usize>,
) -> Vec<(usize, ResourceRequest)> {
    catalogs
        .iter_mut()
        .enumerate()
        .filter(|(index, _)| range.start <= *index && *index <= range.end)
        .filter_map(|(index, catalog)| catalog.first_mut().map(|page| (index, page)))
        .filter(|(_, page)| page.content.is_none())
        .map(|(index, page)| {
            page.content = Some(Loadable::Loading);
            (index, page.request.to_owned())
        })
        .collect()
}

fn load<E: Env + 'static>(request: ResourceRequest) -> Effect {
    EffectFuture::Concurrent(
        E::addon_transport(&request.base)
            .resource(&request.path)
            .map(|result| Msg::Internal(Internal::ResourceRequestResult(request, Box::new(result))))
            .boxed_env(),
    )
    .into()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::seed::seeded_profile;
    use stremio_core::models::common::ResourceError;

    fn selected(r#type: &str) -> Option<Selected> {
        Some(Selected {
            r#type: Some(r#type.to_owned()),
            extra: vec![],
        })
    }

    fn row(request: &ResourceRequest) -> (String, Vec<(String, String)>) {
        (
            request.path.id.clone(),
            request
                .path
                .extra
                .iter()
                .map(|extra| (extra.name.clone(), extra.value.clone()))
                .collect(),
        )
    }

    fn cinemeta_genres(profile: &Profile, r#type: &str) -> Vec<String> {
        let cinemeta = profile
            .addons
            .iter()
            .find(|addon| addon.transport_url == *CINEMETA_URL)
            .unwrap();
        cinemeta
            .manifest
            .catalogs
            .iter()
            .find(|catalog| catalog.id == GENRE_CATALOG_ID && catalog.r#type == r#type)
            .unwrap()
            .extra
            .iter()
            .filter(|prop| prop.name == GENRE_EXTRA_NAME)
            .flat_map(|prop| prop.options.clone())
            .collect()
    }

    #[test]
    fn plan_lists_type_catalogs_then_new_then_genres() {
        let profile = seeded_profile();
        for r#type in ["movie", "series"] {
            let rows: Vec<_> = plan(&selected(r#type), &profile, 2026).iter().map(row).collect();
            let genres = cinemeta_genres(&profile, r#type);
            assert!(!genres.is_empty());
            let mut expected = vec![
                ("top".to_owned(), vec![]),
                ("imdbRating".to_owned(), vec![]),
                ("year".to_owned(), vec![("genre".to_owned(), "2026".to_owned())]),
            ];
            expected.extend(
                genres
                    .into_iter()
                    .map(|genre| ("top".to_owned(), vec![("genre".to_owned(), genre)])),
            );
            assert_eq!(rows, expected, "{type}");
            assert!(plan(&selected(r#type), &profile, 2026)
                .iter()
                .all(|request| request.path.r#type == r#type));
        }
    }

    fn page(id: &str, content: Option<Loadable<Vec<MetaItemPreview>, ResourceError>>) -> Catalog<MetaItemPreview> {
        vec![ResourceLoadable {
            request: ResourceRequest::new(
                CINEMETA_URL.to_owned(),
                ResourcePath::without_extra(CATALOG_RESOURCE_NAME, "movie", id),
            ),
            content,
        }]
    }

    fn ready(name: &str) -> Option<Loadable<Vec<MetaItemPreview>, ResourceError>> {
        let meta = serde_json::from_value(serde_json::json!({"id": name, "type": "movie", "name": name})).unwrap();
        Some(Loadable::Ready(vec![meta]))
    }

    #[test]
    fn range_marks_only_unrequested_rows_in_range() {
        let mut catalogs = vec![page("a", ready("a")), page("b", None), page("c", None), page("d", None)];
        let before = catalogs.clone();
        let marked = mark_range_loading(&mut catalogs, &(0..2));
        assert_eq!(
            marked.iter().map(|(index, request)| (*index, request.path.id.as_str())).collect::<Vec<_>>(),
            [(1, "b"), (2, "c")]
        );
        assert_eq!(catalogs[0], before[0]);
        assert_eq!(catalogs[1][0].content, Some(Loadable::Loading));
        assert_eq!(catalogs[2][0].content, Some(Loadable::Loading));
        assert_eq!(catalogs[3], before[3]);
    }

    #[test]
    fn range_over_requested_rows_changes_nothing() {
        let mut catalogs = vec![page("a", ready("a")), page("b", Some(Loadable::Loading))];
        let before = catalogs.clone();
        assert!(mark_range_loading(&mut catalogs, &(0..5)).is_empty());
        assert_eq!(catalogs, before);
    }

    fn rows(delta: &BoardDelta) -> Vec<usize> {
        delta.rows.iter().map(|row| row.index).collect()
    }

    #[test]
    fn delta_sends_only_rows_changed_since_the_last_read() {
        let mut board = TypeBoard {
            catalogs: vec![page("a", ready("a")), page("b", None), page("c", None)],
            ..Default::default()
        };
        board.touch_all();
        let first = board.delta(0);
        assert_eq!(rows(&first), [0, 1, 2]);
        assert_eq!(first.count, 3);
        let seen = first.version;
        assert!(rows(&board.delta(seen)).is_empty());
        board.touch(2);
        let second = board.delta(seen);
        assert_eq!(rows(&second), [2]);
        assert_eq!(second.count, 3);
        assert!(second.version > seen);
    }

    #[test]
    fn delta_after_a_rebuild_sends_every_row() {
        let mut board = TypeBoard {
            catalogs: vec![page("a", ready("a"))],
            ..Default::default()
        };
        board.touch_all();
        let seen = board.delta(0).version;
        board.catalogs.push(page("b", None));
        board.touch_all();
        assert_eq!(rows(&board.delta(seen)), [0, 1]);
    }

    #[test]
    fn delta_serializes_rows_with_their_index() {
        let mut board = TypeBoard {
            catalogs: vec![page("a", ready("a")), page("b", None)],
            ..Default::default()
        };
        board.touch_all();
        let seen = board.delta(0).version;
        board.touch(1);
        let json = serde_json::to_value(board.delta(seen)).unwrap();
        assert_eq!(json["count"], 2);
        assert_eq!(json["rows"].as_array().unwrap().len(), 1);
        assert_eq!(json["rows"][0]["index"], 1);
        assert_eq!(json["rows"][0]["catalog"][0]["request"]["path"]["id"], "b");
        assert!(json.get("version").is_some() && json.get("selected").is_some());
    }

    #[test]
    fn plan_is_empty_without_a_type() {
        let profile = seeded_profile();
        let none = Some(Selected {
            r#type: None,
            extra: vec![],
        });
        assert!(plan(&none, &profile, 2026).is_empty());
        assert!(plan(&None, &profile, 2026).is_empty());
    }
}
