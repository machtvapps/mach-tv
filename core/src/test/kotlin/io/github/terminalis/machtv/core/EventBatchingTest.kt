package io.github.terminalis.machtv.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EventBatchingTest {
    private fun event(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun newState(vararg fields: String) = event(fields.joinToString(",", """{"name":"NewState","args":[""", "]}") { "\"$it\"" })

    private fun coreEvent(name: String) = event("""{"name":"CoreEvent","args":{"event":"$name","args":"x"}}""")

    @Test
    fun repeatedStatesOfAFieldRefreshItOnce() {
        val steps = eventSteps(listOf(newState("movies"), newState("movies", "ctx"), newState("movies")))
        assertEquals(listOf(EventStep.Refresh(Field.MOVIES), EventStep.Refresh(Field.CTX)), steps)
    }

    @Test
    fun aCoreEventFollowsTheStatesBeforeIt() {
        val steps = eventSteps(listOf(newState("ctx"), coreEvent("AddonInstalled"), newState("ctx"), newState("home")))
        assertEquals(
            listOf(
                EventStep.Refresh(Field.CTX),
                EventStep.Emit(CoreEvent("AddonInstalled", JsonPrimitive("x"))),
                EventStep.Refresh(Field.CTX),
                EventStep.Refresh(Field.HOME),
            ),
            steps,
        )
    }
}
