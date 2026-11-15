package io.booup.roadmate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*
import io.booup.roadmate.pet.PetPlaceSearch
import java.time.LocalDate

private fun petElapsed(millis:Long)="${millis/60_000}분 ${(millis/1000)%60}초"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PetRestContent(viewModel:RoadmateViewModel,openSearch:()->Unit,openHub:()->Unit)=PetGate(viewModel) { state ->
    if(!state.data.enabled) {
        RoadmateCard { Text("반려동물 동행을 켠 뒤 휴식 계획을 시작해 주세요.");GameButton("펫 동행 열기",openHub) }
    } else {
        val data=state.data;val session=data.restSession
        var minutes by remember {mutableStateOf("")}
        var storageConsent by remember {mutableStateOf(false)}
        var stopConfirm by remember {mutableStateOf(false)}
        RoadmateStoryHeader("펫 휴식 도우미","${data.profile?.name.orEmpty()}와 쉬기로 한 계획을 함께 챙겨요.","home-parked.png",3)
        if(session==null) RoadmateCard(tinted=true) {
            Text("보호자가 휴식 간격을 정해 주세요",style=RoadmateType.cardTitle)
            Text("아래 시간은 선택 항목이에요. 반려동물에게 적합한 의학적 휴식 간격을 뜻하지 않아요.")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(30,45,60,90,120).forEach { value -> FilterChip(data.restIntervalMinutes==value,
                    {viewModel.petAction {it.chooseRestInterval(value)}},label={Text("${value}분")},modifier=Modifier.heightIn(min=56.dp)) }
            }
            OutlinedTextField(minutes,{if(it.length<=3 && it.all(Char::isDigit)) minutes=it},
                label={Text("직접 정하기 · 15~240분")},singleLine=true,modifier=Modifier.widthIn(max=380.dp))
            GameButton("이 간격으로 설정",{viewModel.petAction {if(it.chooseRestInterval(minutes.toIntOrNull() ?: 0)) minutes=""}},
                enabled=minutes.toIntOrNull() in 15..240)
            Text(data.restIntervalMinutes?.let {"선택한 계획: ${it}분마다 휴식 확인"} ?: "아직 간격을 선택하지 않았어요.")
            PetCheck("휴식 계획과 확인 기록을 이 기기에 보관하는 데 동의해요.",storageConsent) {storageConsent=!storageConsent}
            Text("선택한 간격과 타이머·확인 횟수를 암호화 보관해요. 동행 종료로 이번 타이머를, 펫 데이터 전체 삭제로 간격까지 지울 수 있어요.",style=MaterialTheme.typography.bodySmall)
            GameButton("휴식 동행 시작",{viewModel.petAction {if(it.startRestSession(storageConsent)) storageConsent=false}},
                primary=true,enabled=data.restIntervalMinutes!=null && storageConsent)
            Text("드라이브 기록 저장과 별도로 사용할 수 있어요.",style=MaterialTheme.typography.bodySmall)
        } else {
            RoadmateCard(tinted=session.due) {
                PetPawMark(color=MaterialTheme.colorScheme.primary)
                Text(when { session.resting->"지금은 함께 쉬는 중"
                    session.paused->"동행 타이머가 일시정지됐어요"
                    session.due->"쉬기로 한 시간이 되었어요"
                    else->"다음 휴식 계획까지 ${petElapsed((session.intervalMinutes*60_000L-session.elapsedSinceRestMillis).coerceAtLeast(0))}" },style=RoadmateType.heading)
                Text("계획 간격 ${session.intervalMinutes}분 · 지난 휴식 확인 후 ${petElapsed(session.elapsedSinceRestMillis)}")
                Text("이번 동행 타이머 ${petElapsed(session.elapsedTotalMillis)} · 보호자 휴식 확인 ${session.confirmedRests}회")
                if(session.paused && !session.resting) Text("앱 재시작·화면 이탈·차량 상태 미확인 구간은 더하지 않아요. 정차 상태에서 직접 이어갈 수 있어요.")
                if(session.due && !session.resting) Text("정차만으로 휴식을 마쳤다고 처리하지 않아요. 함께 쉬었다면 직접 확인해 주세요.")
                if(session.resting) {
                    Text("보호자가 직접 확인한 항목을 골라 주세요.",style=MaterialTheme.typography.titleMedium)
                    listOf(PetRestCheck.WATER to "물과 물품을 확인했어요",PetRestCheck.WALK to "산책이나 바깥 휴식을 확인했어요",
                        PetRestCheck.BELONGINGS to "이동용품을 다시 확인했어요").forEach { (item,label) ->
                        PetCheck(label,item in session.checks) {viewModel.petAction {it.toggleRestCheck(item)}}
                    }
                    GameButton("휴식 확인하고 다시 시작",{viewModel.petAction {it.confirmPetRest()}},primary=true,enabled=session.checks.isNotEmpty())
                    GameButton("휴식 확인 없이 돌아가기",{viewModel.petAction {it.cancelPetRest()}})
                } else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    GameButton("함께 쉬기 시작",{viewModel.petAction {it.beginPetRest()}},primary=true)
                    if(session.paused) GameButton("타이머 이어가기",{viewModel.petAction {it.resumeRestSession()}})
                    else GameButton("타이머 일시정지",{viewModel.petAction {it.pauseRestSession()}})
                }
                TextButton(onClick={stopConfirm=true},modifier=Modifier.heightIn(min=56.dp)) {Text("이번 휴식 동행 종료")}
            }
        }
        RoadmateCard(compact=true) {
            Text("함께 이용할 장소를 찾고 있나요?",style=RoadmateType.cardTitle)
            Text("지역을 직접 골라 공개된 반려동물 동반 조건을 확인해요.")
            GameButton("동반 조건 검색",openSearch)
        }
        RoadmateCard(compact=true) {
            Text("이 타이머가 세는 시간",style=RoadmateType.cardTitle)
            Text("앱이 열려 있고 차량 움직임 정보가 확인되는 동행 시간이에요. 정차 대기 시간도 포함하고, ‘함께 쉬기 시작’에서는 멈춰요. 연속 운전 시간이나 실제 탑승·건강 상태를 판단하지 않아요.")
            Text("주행 중에는 상세·조작을 잠그고 정차 후 계획을 보여줘요. 백그라운드 알림·음성 안내는 제공하지 않아요.")
        }
        GameButton("펫 동행으로",openHub)
        if(stopConfirm) AlertDialog(onDismissRequest={stopConfirm=false},title={Text("이번 휴식 동행을 종료할까요?")},
            text={Text("현재 타이머와 이번 확인 횟수를 지워요. 선택한 간격과 기존 엽서·여행·XP는 유지해요.")},
            confirmButton={GameButton("휴식 동행 종료",{viewModel.petAction {if(it.stopRestSession()) stopConfirm=false}},primary=true)},
            dismissButton={GameButton("계속하기",{stopConfirm=false})})
    }
}

private fun PetPlaceRegion.label()=when(this) {PetPlaceRegion.SEOUL->"서울";PetPlaceRegion.GANGNEUNG->"강릉";PetPlaceRegion.JEJU->"제주"}
private fun PetPlaceCategory.label()=when(this) {PetPlaceCategory.PLAYGROUND->"놀이터";PetPlaceCategory.EXPERIENCE->"체험";PetPlaceCategory.PARK->"공원"}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PetPlaceSearchContent(viewModel:RoadmateViewModel,openHub:()->Unit)=PetGate(viewModel) { state ->
    val vehicle by viewModel.state.collectAsStateWithLifecycle()
    if(!state.data.enabled) {
        RoadmateCard { Text("반려동물 동행을 켜고 우리 아이의 동반 조건을 살펴보세요.");GameButton("펫 동행 열기",openHub) }
    } else {
        var region by remember {mutableStateOf<PetPlaceRegion?>(null)}
        var category by remember {mutableStateOf<PetPlaceCategory?>(null)}
        var keyword by remember {mutableStateOf("")}
        var weight by remember {mutableStateOf("")}
        var carrier by remember {mutableStateOf(PetCarrierAnswer.UNKNOWN)}
        var detail by remember {mutableStateOf<PetPlaceMatch?>(null)}
        val search=remember {PetPlaceSearch()}
        val today=LocalDate.now()
        val invalidWeight=weight.isNotBlank() && weight.toDoubleOrNull()?.let {it.isFinite() && it>0 && it<=200}!=true
        val kind=state.data.profile?.kind ?: PetKind.OTHER
        val matches=remember(region,category,keyword,weight,carrier,kind,today) {
            if(invalidWeight) emptyList() else search.search(PetPlaceQuery(region,keyword,kind,weight.toDoubleOrNull(),carrier,category),today)
        }
        RoadmateStoryHeader("동반 조건 검색","우리 아이와 함께 이용할 후보를 살펴보세요.","map-gangneung.png",1)
        RoadmateCard(compact=true,tinted=true) {
            Text("공식 안내를 담은 오프라인 검색",style=RoadmateType.cardTitle)
            Text("서울·강릉·제주 8곳 · 정보 확인일 2026-11-15 · 실시간 운영·입장 가능 보장이 아니에요.")
            Text("현재 위치와 경로를 수집하지 않아요. 지역은 직접 선택해 주세요.",style=MaterialTheme.typography.bodySmall)
            if(vehicle.mode==RuntimeMode.DEMO) Text("장소 안내는 공식 공개 정보이며 모의 이동 데이터와 별개예요.",style=MaterialTheme.typography.bodySmall)
        }
        RoadmateCard {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(region==null,{region=null},label={Text("모든 지역")},modifier=Modifier.heightIn(min=56.dp))
                PetPlaceRegion.entries.forEach { item -> FilterChip(region==item,{region=item},label={Text(item.label())},modifier=Modifier.heightIn(min=56.dp)) }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(category==null,{category=null},label={Text("모든 장소")},modifier=Modifier.heightIn(min=56.dp))
                PetPlaceCategory.entries.forEach { item -> FilterChip(category==item,{category=item},label={Text(item.label())},modifier=Modifier.heightIn(min=56.dp)) }
            }
            OutlinedTextField(keyword,{if(it.length<=80) keyword=it},label={Text("이름·지역·조건 검색")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(weight,{if(it.length<=6 && it.all {c->c.isDigit() || c=='.'}) weight=it},
                label={Text("우리 아이 체중 kg · 선택")},singleLine=true,isError=invalidWeight,modifier=Modifier.widthIn(max=400.dp))
            if(invalidWeight) Text("체중은 0보다 크고 200 이하인 숫자로 입력해 주세요.")
            Text("이동장 준비 여부",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(PetCarrierAnswer.UNKNOWN to "아직 미확인",PetCarrierAnswer.YES to "이동장 준비",PetCarrierAnswer.NO to "이동장 없음").forEach { (value,label) ->
                    FilterChip(carrier==value,{carrier=value},label={Text(label)},modifier=Modifier.heightIn(min=56.dp))
                }
            }
            Text("체중과 이동장 선택은 검색 화면에서만 사용하고 저장하지 않아요.",style=MaterialTheme.typography.bodySmall)
        }
        Text("검색 결과 ${matches.size}곳",style=RoadmateType.cardTitle)
        if(matches.isEmpty()) RoadmateCard {Text(if(invalidWeight) "입력한 체중을 확인해 주세요." else "저장된 공식 안내에서 찾지 못했어요. 지역·종류·검색어를 바꿔 보세요. 전국 전체 목록은 아니에요.")}
        matches.forEach { match -> RoadmateCard(compact=true,tinted=match.fit==PetConditionFit.PARTIAL_MATCH) {
            Text(match.place.name,style=RoadmateType.cardTitle)
            Text("${match.place.region.label()} · ${match.place.category.label()} · ${when(match.fit) {
                PetConditionFit.PARTIAL_MATCH->"일부 조건 일치";PetConditionFit.CHECK_REQUIRED->"추가 확인 필요";PetConditionFit.CONFLICT->"명시된 조건과 맞지 않음"
            }}")
            Text(match.place.conditions)
            if(match.conflicts.isNotEmpty()) Text(match.conflicts.joinToString("\n"),color=MaterialTheme.colorScheme.error)
            Text("${match.place.sourceName} · 확인일 ${match.place.checkedOn}",style=MaterialTheme.typography.bodySmall)
            GameButton("동반 조건 자세히",{detail=match})
        } }
        GameButton("펫 동행으로",openHub)
        detail?.let { match -> AlertDialog(onDismissRequest={detail=null},title={Text(match.place.name)},text={
            Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(match.place.address)
                Text(match.place.conditions)
                match.matched.forEach {Text("확인한 내용 · $it")}
                match.conflicts.forEach {Text(it,color=MaterialTheme.colorScheme.error)}
                match.needsCheck.forEach {Text("확인할 내용 · $it")}
                Text("이용 구역 · ${match.place.allowedArea ?: "추가 확인 필요"}")
                Text("입장을 확정하지 않아요. 종류·견종·현장 제한·운영 여부를 방문 전에 직접 확인해 주세요.")
                Text("${match.place.sourceName}\n안내 확인일 ${match.place.checkedOn}\n원문 갱신일 ${match.place.sourceModifiedOn ?: "미제공"}")
                SelectionContainer {Text(match.place.sourceUrl,style=MaterialTheme.typography.bodySmall)}
            }
        },confirmButton={GameButton("닫기",{detail=null},primary=true)}) }
    }
}
