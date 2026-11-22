#[cfg(test)]
mod tests {
    use stremio_core::runtime::msg::Action;

    #[test]
    fn kotlin_actions_parse_as_core_actions() {
        let actions = [
            r#"{"action":"Load","args":{"model":"CatalogsWithExtra","args":{"type":"movie","extra":[]}}}"#,
            r#"{"action":"Load","args":{"model":"CatalogsWithExtra","args":{"type":null,"extra":[["search","pioneer one"]]}}}"#,
            r#"{"action":"CatalogsWithExtra","args":{"action":"LoadRange","args":{"start":0,"end":3}}}"#,
            r#"{"action":"Load","args":{"model":"CatalogWithFilters","args":{"request":{"base":"https://v3-cinemeta.strem.io/manifest.json","path":{"resource":"catalog","type":"movie","id":"top","extra":[["genre","Action"]]}}}}}"#,
            r#"{"action":"CatalogWithFilters","args":{"action":"LoadNextPage"}}"#,
            r#"{"action":"Load","args":{"model":"LocalSearch"}}"#,
            r#"{"action":"Search","args":{"action":"Search","args":{"searchQuery":"pioneer","maxResults":8}}}"#,
            r#"{"action":"Load","args":{"model":"LibraryWithFilters","args":{"request":{"type":null,"sort":"lastwatched","page":1}}}}"#,
            r#"{"action":"Load","args":{"model":"LibraryWithFilters","args":{"request":{"type":"series","sort":"namereverse","page":1}}}}"#,
            r#"{"action":"LibraryWithFilters","args":{"action":"LoadNextPage"}}"#,
            r#"{"action":"Ctx","args":{"action":"PullNotifications"}}"#,
            r##"{"action":"Ctx","args":{"action":"UpdateSettings","args":{"interfaceLanguage":"eng","interfaceScale":100,"hideSpoilers":false,"gamepadSupport":false,"streamingServerUrl":"http://127.0.0.1:11470/","playerType":null,"bingeWatching":true,"playInBackground":true,"hardwareDecoding":true,"gpuVideoProcessing":false,"videoMode":null,"frameRateMatchingStrategy":"Disabled","nextVideoNotificationDuration":35000,"audioPassthrough":false,"audioLanguage":"eng","secondaryAudioLanguage":null,"subtitlesLanguage":null,"secondarySubtitlesLanguage":null,"subtitlesAutoSelect":true,"subtitlesSize":125,"subtitlesFont":"Roboto","subtitlesBold":false,"subtitlesOffset":5,"subtitlesTextColor":"#FFFF00FF","subtitlesBackgroundColor":"#000000FF","subtitlesOutlineColor":"#000000","subtitlesOpacity":100,"assSubtitlesStyling":false,"escExitFullscreen":true,"seekTimeDuration":10000,"seekShortTimeDuration":3000,"pauseOnMinimize":false,"quitOnClose":true,"surroundSound":false,"streamingServerWarningDismissed":null,"serverInForeground":false,"sendCrashReports":true,"discordRpcEnabled":false}}}"##,
        ];
        for action in actions {
            serde_json::from_str::<Action>(action).unwrap_or_else(|error| panic!("{action}: {error}"));
        }
    }
}
