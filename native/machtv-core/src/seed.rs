use stremio_core::types::profile::Profile;

const SEEDED_ADDON_IDS: [&str; 2] = ["com.linvo.cinemeta", "org.stremio.opensubtitlesv3"];
const SUBTITLE_BACKGROUND: &str = "#00000099";

pub fn seeded_profile() -> Profile {
    let mut profile = Profile::default();
    profile
        .addons
        .retain(|addon| SEEDED_ADDON_IDS.contains(&addon.manifest.id.as_str()));
    profile.settings.subtitles_language = None;
    profile.settings.subtitles_background_color = SUBTITLE_BACKGROUND.to_owned();
    profile
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn seeded_profile_keeps_only_cinemeta_and_opensubtitles_v3() {
        let ids: Vec<String> = seeded_profile()
            .addons
            .iter()
            .map(|addon| addon.manifest.id.clone())
            .collect();
        assert_eq!(ids, ["com.linvo.cinemeta", "org.stremio.opensubtitlesv3"]);
    }

    #[test]
    fn seeded_profile_starts_with_subtitles_off_on_a_translucent_background() {
        let settings = seeded_profile().settings;
        assert_eq!(settings.subtitles_language, None);
        assert_eq!(settings.subtitles_background_color, "#00000099");
    }
}
