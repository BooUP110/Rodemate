package io.booup.roadmate.care

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class CareArbitratorTest {
    private fun event(id: String, priority: CarePriority, category: CareCategory = CareCategory.REST,
        occurred: Long = 100, rule: RuleId = RuleId.REST, subject: String = id) = CareEvent(
        id, CareKind.REST, priority, DataSource.SIMULATED, occurred, rule, category, emptyList(),
        "test.key", subject = subject)

    @Test fun explicitPriorityOrderingSupportsP1ThroughP4() {
        val values = CarePriority.entries.map { event(it.name, it) }
        assertEquals(values, CareArbitrator.order(values.reversed()))
    }
    @Test fun categoryOrderIsExplicitAndIndependentOfInputOrder() {
        val warning = event("warning", CarePriority.P1_SAFETY, CareCategory.VEHICLE_WARNING)
        val light = event("light", CarePriority.P1_SAFETY, CareCategory.LIGHTING)
        val hvac = event("hvac", CarePriority.P2_SUGGESTION, CareCategory.HVAC)
        val rest = event("rest", CarePriority.P2_SUGGESTION)
        assertEquals(listOf(warning, light, hvac, rest), CareArbitrator.order(listOf(rest, light, hvac, warning)))
    }
    @Test fun sameCategoryUsesOccurrenceThenRuleThenSubjectThenId() {
        val old = event("old", CarePriority.P2_SUGGESTION, occurred = 99)
        val hvac = event("hvac", CarePriority.P2_SUGGESTION, rule = RuleId.HVAC)
        val a = event("a", CarePriority.P2_SUGGESTION, subject = "subject")
        val b = event("b", CarePriority.P2_SUGGESTION, subject = "subject")
        assertEquals(listOf(old, hvac, a, b), CareArbitrator.order(listOf(b, a, hvac, old)))
    }
    @Test fun allRotationsOfSamePriorityEventsGiveIdenticalOrder() {
        val values = (1..8).map { event("event$it", CarePriority.P2_SUGGESTION, occurred = it.toLong()) }
        values.indices.forEach { start ->
            assertEquals(values, CareArbitrator.order(values.drop(start) + values.take(start)))
            assertEquals(values, CareArbitrator.order((values.drop(start) + values.take(start)).reversed()))
        }
    }
    @Test fun lowerPriorityCannotWinByEarlierTimeOrCategory() {
        val p1 = event("p1", CarePriority.P1_SAFETY, CareCategory.REST, 500)
        val p2 = event("p2", CarePriority.P2_SUGGESTION, CareCategory.VEHICLE_WARNING, 0)
        assertEquals(p1, CareArbitrator.order(listOf(p2, p1)).first())
    }
}
