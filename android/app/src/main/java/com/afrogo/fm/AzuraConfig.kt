package com.afrogo.fm

/** Endpoints públicos de la estación. El catálogo privado se obtiene a través del gateway HTTPS de Afrogo. */
object AzuraConfig {
    const val BASE_URL = "https://afrogochincha.duckdns.org"
    const val STATION_SLUG = "afrogo-fm"
    const val PUBLIC_PAGE_URL = "https://afrogochincha.duckdns.org/public/afrogo-fm"
    // Ruta estándar de AzuraCast; confirma el mount point en Public > Listen.
    const val STREAM_URL = "https://afrogochincha.duckdns.org/listen/afrogo-fm/radio.mp3"
    const val NOW_PLAYING_URL = "https://afrogochincha.duckdns.org/api/nowplaying/afrogo-fm"
    const val STATION_NAME = "Afrogo FM"
    const val STATION_SUBTITLE = "Radio oficial · Música en vivo"
    val API_BASE_URL: String get() = BuildConfig.AFROGO_API_BASE_URL.trim().trimEnd('/')
}
