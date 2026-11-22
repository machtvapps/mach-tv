package io.github.terminalis.machtv.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal sealed interface EventStep {
    data class Refresh(val field: Field) : EventStep
    data class Emit(val event: CoreEvent) : EventStep
}

internal fun eventSteps(events: List<JsonObject>): List<EventStep> {
    val steps = mutableListOf<EventStep>()
    val pending = LinkedHashSet<Field>()
    fun flush() {
        pending.mapTo(steps) { EventStep.Refresh(it) }
        pending.clear()
    }
    for (event in events) {
        when (event.getValue("name").jsonPrimitive.content) {
            "NewState" -> event.getValue("args").jsonArray.forEach { element ->
                pending += Field.entries.first { it.key == element.jsonPrimitive.content }
            }
            "CoreEvent" -> {
                flush()
                val args = event.getValue("args").jsonObject
                steps += EventStep.Emit(CoreEvent(args.getValue("event").jsonPrimitive.content, args["args"]))
            }
        }
    }
    flush()
    return steps
}
