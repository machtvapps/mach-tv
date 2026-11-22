package io.github.terminalis.machtv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineSettingsTest {
    private val tenGigabytes = 10L * 1024 * 1024 * 1024

    @Test
    fun readsTheEngineValues() {
        val values = Json.parseToJsonElement(
            """{"cacheSize":10737418240.0,"btEnableDht":false,"btProxyType":"socks5Password","btProxyHost":"10.0.0.1","btProxyPort":1080,"btProxyUsername":"u","btProxyPassword":"p","btOutgoingInterfaces":"tun0"}""",
        ).jsonObject
        assertEquals(EngineSettings(tenGigabytes, false, ProxyKind.Socks5, "10.0.0.1", 1080, "u", "p", "tun0"), engineSettings(values))
    }

    @Test
    fun missingFieldsReadAsTheEngineDefaults() {
        assertEquals(EngineSettings(0, true, ProxyKind.Off, "", 0, "", "", ""), engineSettings(JsonObject(emptyMap())))
    }

    @Test
    fun cacheLabels() {
        assertEquals("10 GB", cacheLabel(tenGigabytes))
        assertEquals("No limit", cacheLabel(0))
        assertEquals("3 GB", cacheLabel(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun proxyTypeUsesThePasswordVariantWithAUsername() {
        assertEquals("none", proxyType(ProxyKind.Off, "u"))
        assertEquals("socks5", proxyType(ProxyKind.Socks5, ""))
        assertEquals("socks5Password", proxyType(ProxyKind.Socks5, "u"))
        assertEquals("httpPassword", proxyType(ProxyKind.Http, "u"))
    }

    @Test
    fun interfaceFieldsBindByName() {
        assertEquals("""{"btOutgoingInterfaces":"tun0","btListenInterfaces":"tun0:42000-42010"}""", interfaceFields("tun0").toString())
        assertEquals("""{"btOutgoingInterfaces":"","btListenInterfaces":"0.0.0.0:42000-42010,[::]:42000-42010"}""", interfaceFields(null).toString())
    }
}
