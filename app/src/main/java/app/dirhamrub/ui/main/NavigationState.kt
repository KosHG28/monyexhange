package app.dirhamrub.ui.main

/** Navigation/settings do not subscribe to calculator keystrokes. A single canonical state remains. */
internal fun sameNavigationState(a: MainUiState, b: MainUiState): Boolean =
    a.loaded == b.loaded && a.wallet === b.wallet && a.official === b.official &&
    a.officialWeekAgo === b.officialWeekAgo && a.ratesLoading == b.ratesLoading &&
    a.ratesFailed == b.ratesFailed && a.unionPayLoading == b.unionPayLoading &&
    a.unionPayFailed == b.unionPayFailed && a.countryHandled == b.countryHandled &&
    a.onboardingDone == b.onboardingDone && a.cameraEmbedded == b.cameraEmbedded &&
    a.ratesHistory === b.ratesHistory && a.cameraUsed == b.cameraUsed &&
    a.tileOffered == b.tileOffered && a.unionPay === b.unionPay && a.settings == b.settings &&
    a.officialFetchedAt == b.officialFetchedAt && a.officialSource == b.officialSource &&
    a.unionPayFetchedAt == b.unionPayFetchedAt && a.guides === b.guides
