package io.booup.roadmate.pet

import io.booup.roadmate.model.*
import java.util.Collections

/** Real public facts reviewed 2026-11-15; not synthetic Demo records or a live TourAPI response.
 * Only concise factual summaries; no photographs, article passages, maps or third-party characters.
 * Add/change a typed restriction only after checking the linked primary source.
 */
object PetPublicPlaceCatalog {
    private const val CHECKED="2026-11-15"
    private const val SEOUL="https://func.seoul.go.kr/animal/playGroundList.do"
    private fun playground(id:String,name:String,address:String)=PetPublicPlace(
        id,name,PetPlaceRegion.SEOUL,PetPlaceCategory.PLAYGROUND,address,setOf(PetKind.DOG),
        conditions="등록된 반려견과 보호자가 함께 이용하는 놀이터예요. 입·퇴장 때 목줄 또는 가슴줄을 착용하고 배변봉투를 준비해 주세요. 출입 제한 대상과 현장 운영 여부를 확인해 주세요.",
        allowedArea="반려견 놀이터 구역. 공원 전체의 출입 허용을 뜻하지 않아요.",
        sourceName="서울특별시 반려견 놀이터 안내",sourceUrl=SEOUL,checkedOn=CHECKED,
    )
    val places:List<PetPublicPlace> = Collections.unmodifiableList(listOf(
        playground("seoul-worldcup","월드컵공원 반려견 놀이터","서울 마포구 증산로 32 · 평화의공원 주차장 옆"),
        playground("seoul-boramae","보라매공원 반려견 놀이터","서울 동작구 여의대방로20마길 11 · 향기원 옆"),
        playground("seoul-children","어린이대공원 반려견 놀이터","서울 광진구 능동로 216 · 구의문 주차장 옆"),
        playground("seoul-maeheon","매헌시민의숲 반려견 놀이터","서울 서초구 매헌로 99 · 양재천교 인근"),
        PetPublicPlace("gangneung-rail","정동진 레일바이크",PetPlaceRegion.GANGNEUNG,PetPlaceCategory.EXPERIENCE,
            "강릉시 정동진역 탑승장",emptySet(),weightBelowKg=6.0,carrierRequired=true,
            conditions="운영사 FAQ는 6kg 미만 초소형 반려동물 한 마리와 잠금 가능한 케이지를 조건으로 안내해요. 케이지에 머리까지 넣고 안아야 하며, 별도로 양손 조작 가능한 성인 동승자가 필요해요. 동물 종류는 문의해 주세요.",
            allowedArea="매표소에서 탑승장으로 이동할 때부터 이용 중까지 케이지 사용 조건",
            sourceName="코레일관광개발 전국 통합 레일바이크 FAQ",
            sourceUrl="https://www.railtrip.co.kr/homepage/jeongdongjin/0402.php",checkedOn=CHECKED),
        PetPublicPlace("jeju-hueree","휴애리자연생활공원",PetPlaceRegion.JEJU,PetPlaceCategory.PARK,
            "제주 서귀포시 남원읍 신례동로 256",setOf(PetKind.DOG),weightBelowKg=7.0,
            conditions="7kg 미만 소형견의 일부 구역 동반 안내가 있어요. 2m 이하 목줄·가슴줄과 배변봉투를 준비하고, 혼잡한 곳에서는 안거나 이동장·유모차를 이용하도록 안내해요. 카페 내부는 출입 불가로 안내돼요.",
            allowedArea="일부 구역. 카페 내부 제외, 야외 이용 범위는 현장 확인",
            sourceName="제주관광공사 VISITJEJU",sourceUrl="https://www.visitjeju.net/kr/detail/view?contentsid=CONT_000000000500707",
            checkedOn=CHECKED,sourceModifiedOn="2026-11-14"),
        PetPublicPlace("jeju-vicheollin","비체올린",PetPlaceRegion.JEJU,PetPlaceCategory.EXPERIENCE,
            "제주 제주시 한경면 판조로 253-6",setOf(PetKind.DOG),
            conditions="소·중·대형견 입장 안내가 있어요. 배변과 리드줄 관리가 조건이며, 카약은 안고 탈 수 있는 체격일 때 동반 안내가 있어요. 체험별 추가 조건을 확인해 주세요.",
            allowedArea="입장과 체험별 조건이 다름. 카약은 체격 조건 추가",
            sourceName="제주관광공사 VISITJEJU",sourceUrl="https://www.visitjeju.net/kr/detail/view?contentsid=CNTS_000000000000614",
            checkedOn=CHECKED,sourceModifiedOn="2026-11-08"),
        PetPublicPlace("jeju-gilgal","길갈 팜 랜드",PetPlaceRegion.JEJU,PetPlaceCategory.PARK,
            "제주 제주시 조천읍 남조로 1785-12",emptySet(),
            conditions="반려동물 동반 입장 안내가 있어요. 종류·체중·이동장과 이용 구역의 세부 조건은 공개 안내에서 확인되지 않았어요. 방문 전에 문의해 주세요.",
            allowedArea=null,sourceName="제주관광공사 VISITJEJU",
            sourceUrl="https://www.visitjeju.net/kr/detail/view?contentsid=CNTS_300000000013693",
            checkedOn=CHECKED,sourceModifiedOn="2026-09-28"),
    ))
}
