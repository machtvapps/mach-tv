package io.github.machtvapps.machtv.core

import kotlinx.serialization.json.JsonElement

data class CoreEvent(val name: String, val args: JsonElement?)
