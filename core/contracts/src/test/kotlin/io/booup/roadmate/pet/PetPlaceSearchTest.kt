package io.booup.roadmate.pet

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PetPlaceSearchTest {
    private val search=PetPlaceSearch()
    private val day=LocalDate.of(2026,11,15)
    @Test fun realCatalogHasEightUniquePrimarySourcedPlacesAndNoInventedCoverage() {
        val all=search.search(PetPlaceQuery(),day)
        assertEquals(8,all.size);assertEquals(8,all.map {it.place.id}.distinct().size)
        assertEquals(4,search.search(PetPlaceQuery(region=PetPlaceRegion.SEOUL),day).size)
        assertEquals(1,search.search(PetPlaceQuery(region=PetPlaceRegion.GANGNEUNG),day).size)
        assertEquals(3,search.search(PetPlaceQuery(region=PetPlaceRegion.JEJU),day).size)
        assertTrue(all.all {it.place.sourceUrl.startsWith("https://") && it.place.checkedOn=="2026-11-15"})
    }
    @Test fun regionKeywordAndCategoryReallyFilter() {
        val result=search.search(PetPlaceQuery(region=PetPlaceRegion.SEOUL,keyword="월드컵",category=PetPlaceCategory.PLAYGROUND),day)
        assertEquals("seoul-worldcup",result.single().place.id)
        assertTrue(search.search(PetPlaceQuery(keyword="NONEXISTENT"),day).isEmpty())
        assertTrue(search.search(PetPlaceQuery(region=PetPlaceRegion.JEJU,category=PetPlaceCategory.PLAYGROUND),day).isEmpty())
    }
    @Test fun railOperatorStrictSixKgBoundaryAndMandatoryCarrierAreKept() {
        fun match(weight:Double,carrier:PetCarrierAnswer)=search.search(PetPlaceQuery(keyword="정동진",weightKg=weight,carrier=carrier),day).single()
        assertTrue(match(5.9,PetCarrierAnswer.YES).conflicts.isEmpty())
        assertTrue(match(6.0,PetCarrierAnswer.YES).conflicts.any {it.contains("6kg 미만")})
        assertTrue(match(5.0,PetCarrierAnswer.NO).conflicts.any {it.contains("이동장")})
        assertEquals(PetConditionFit.CONFLICT,match(6.1,PetCarrierAnswer.YES).fit)
    }
    @Test fun huereeStrictSevenKgBoundaryIsNotInclusive() {
        val result=search.search(PetPlaceQuery(keyword="휴애리",weightKg=7.0),day).single()
        assertEquals(PetConditionFit.CONFLICT,result.fit);assertTrue(result.place.conditions.contains("카페 내부"))
    }
    @Test fun missingSpeciesDoesNotBecomeApprovalOrDenialForCats() {
        val all=search.search(PetPlaceQuery(kind=PetKind.CAT),day)
        assertTrue(all.all {it.fit==PetConditionFit.CHECK_REQUIRED})
        assertTrue(all.all {it.needsCheck.any {text->text.contains("동물 종류")}})
    }
    @Test fun missingLimitsAndCarrierRemainVisibleUnknowns() {
        val match=search.search(PetPlaceQuery(keyword="월드컵",weightKg=10.0,carrier=PetCarrierAnswer.NO),day).single()
        assertEquals(PetConditionFit.PARTIAL_MATCH,match.fit)
        assertTrue(match.needsCheck.any {it.contains("체중 제한")});assertTrue(match.needsCheck.any {it.contains("이동장")})
        assertTrue(match.conflicts.isEmpty())
    }
    @Test fun expiredOrFutureCheckedDateRequiresReconfirmation() {
        for(today in listOf(day.minusDays(1),day.plusDays(31))) {
            val all=search.search(PetPlaceQuery(),today)
            assertTrue(all.all {it.snapshotStale});assertTrue(all.all {it.fit==PetConditionFit.CHECK_REQUIRED})
        }
    }
    @Test fun malformedWeightDoesNotYieldFalseMatch() {
        for(weight in listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,-1.0,201.0))
            assertTrue(search.search(PetPlaceQuery(weightKg=weight),day).isEmpty())
    }
    @Test fun noSpecificConditionsAreInferredFromGenericPetAdmission() {
        val place=search.search(PetPlaceQuery(keyword="길갈"),day).single()
        assertEquals(PetConditionFit.CHECK_REQUIRED,place.fit);assertNull(place.place.weightBelowKg)
        assertNull(place.place.allowedArea);assertNull(place.place.carrierRequired)
    }
}
