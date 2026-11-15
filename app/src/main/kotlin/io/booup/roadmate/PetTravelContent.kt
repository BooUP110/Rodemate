package io.booup.roadmate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*

private fun PetKind.label()=when(this) { PetKind.DOG->"반려견"; PetKind.CAT->"반려묘"; PetKind.OTHER->"다른 반려동물" }
private fun PetTravelPurpose.label()=when(this) { PetTravelPurpose.OUTING->"나들이"; PetTravelPurpose.GROOMING->"미용 · 맡김"; PetTravelPurpose.CLINIC->"병원 이동" }
private fun PetPackingItem.label()=when(this) {
    PetPackingItem.WATER_KIT->"물그릇과 물 챙기기"; PetPackingItem.WALKING_BAG->"산책용품과 가방 확인"
    PetPackingItem.CARRIER->"이동용품 준비 확인"; PetPackingItem.COMFORT_ITEM->"익숙한 담요나 물품 챙기기"
}
private fun PetCardFrame.label()=when(this) { PetCardFrame.PAPER->"기본 편지지"; PetCardFrame.PAW->"발도장 프레임"; PetCardFrame.STAR->"별빛 프레임" }

/** Original code-drawn mark, using the existing mint/gold visual language. */
@Composable
internal fun PetPawMark(modifier:Modifier=Modifier,color:Color=GameGold) {
    Canvas(modifier.size(42.dp)) {
        drawOval(color,Offset(size.width*.25f,size.height*.48f),Size(size.width*.5f,size.height*.4f))
        listOf(.18f to .36f,.4f to .22f,.64f to .22f,.83f to .36f).forEach { (x,y) ->
            drawOval(color,Offset(size.width*(x-.09f),size.height*(y-.11f)),Size(size.width*.18f,size.height*.24f))
        }
    }
}

@Composable
internal fun PetGate(viewModel:RoadmateViewModel,content:@Composable (PetTravelUiState)->Unit) {
    val state by viewModel.petTravelUiState.collectAsStateWithLifecycle()
    when {
        state.error!=null -> RoadmateCard {
            Text(UiCopy.storageError(requireNotNull(state.error)))
            GameButton("펫 기록 다시 읽기", { viewModel.petAction { it.initialize() } })
        }
        !state.loaded -> RoadmateCard { Text("동행 수첩을 불러오고 있어요.") }
        !state.contentAllowed -> RoadmateCard { Text("최신 정차 상태와 화면 허용을 확인한 뒤 펫 동행을 열 수 있어요.") }
        else -> content(state)
    }
}

@Composable
internal fun PetCheck(label:String,checked:Boolean,onToggle:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(12.dp))
        .toggleable(checked,role=Role.Checkbox,onValueChange={onToggle()}).padding(horizontal=6.dp,vertical=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Checkbox(checked,onCheckedChange=null)
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun PetTravelEntry(viewModel:RoadmateViewModel,title:String,body:String,open:()->Unit) {
    val pet by viewModel.petTravelUiState.collectAsStateWithLifecycle()
    if(!pet.contentAllowed || !pet.data.enabled) return
    RoadmateCard(compact=true,tinted=true) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            PetPawMark(color=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("${pet.data.profile?.name.orEmpty()}와 $title",style=RoadmateType.cardTitle)
                Text(body,style=MaterialTheme.typography.bodyMedium)
            }
        }
        GameButton(title,open)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PetTravelHub(viewModel:RoadmateViewModel,openPass:()->Unit,openPlaces:()->Unit,openCards:()->Unit,
    openMap:()->Unit,openSettings:()->Unit,openRest:()->Unit,openSearch:()->Unit) = PetGate(viewModel) { state ->
    val data=state.data
    val profile=data.profile
    val plan=data.plan
    var editing by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    if(profile==null) {
        RoadmateStoryHeader("함께 타는 아이가 있나요?","익숙한 동반자와 함께, 우리 아이의 이동을 기억해요.","home-parked.png",3)
        RoadmateCard {
            Text("선택해서 사용하는 반려동물 동행",style=RoadmateType.cardTitle)
            Text("출발 준비부터 정차 확인, 우리만의 엽서와 다음 여행의 메모까지 연결해요.")
            Text("기존 여행·캐릭터·시즌은 그대로 이용할 수 있어요.")
            GameButton("우리 아이 등록",{editing=true},primary=true)
        }
    } else {
        RoadmateCard(tinted=true) {
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                PetPawMark(color=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("${profile.name}와 함께",style=RoadmateType.heading)
                    Text("${profile.kind.label()} · ${if(data.enabled) "반려동물 동행 중" else "일반 동행 모드"}")
                    if(PetPassReward.WELCOME_BADGE in data.claimed) Text("동행 발자국 배지",color=MaterialTheme.colorScheme.primary)
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                GameButton(if(data.enabled) "일반 모드로 전환" else "반려동물 모드 켜기",{
                    if(data.enabled && (plan!=null || data.restSession!=null)) confirmation="mode-off"
                    else viewModel.petAction { it.setEnabled(!data.enabled) }
                },primary=!data.enabled)
                GameButton("프로필 수정",{editing=true},enabled=plan==null && data.restSession==null)
            }
            if(!data.enabled) Text("등록한 수첩과 엽서는 보관하고, 기존 여행 화면을 이용해요.")
            if(plan!=null || data.restSession!=null) Text("프로필은 동행 준비와 휴식 타이머를 종료한 뒤 수정할 수 있어요.",style=MaterialTheme.typography.bodySmall)
        }
        if(data.enabled) {
            RoadmateCard(tinted=true) {
                Text("우리 아이의 이동 도우미",style=RoadmateType.cardTitle)
                Text(data.restSession?.let { if(it.due) "쉬기로 한 시간이 되었어요. 정차한 지금 휴식 계획을 확인해 주세요."
                    else "휴식 계획 ${it.intervalMinutes}분 · 보호자 확인 ${it.confirmedRests}회" }
                    ?: "휴식 계획을 정하고, 함께 이용할 장소의 동반 조건을 살펴보세요.")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    GameButton("펫 휴식 도우미",openRest,primary=true)
                    GameButton("동반 조건 검색",openSearch)
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                GameButton("첫 차 나들이 패스",openPass,primary=true)
                GameButton("우리 아이 장소 수첩",openPlaces)
                GameButton("함께한 펫 엽서 ${state.cards.size}장",openCards)
            }
            RoadmateJournalColumns(firstWeight=.52f,first={
                RoadmateCard {
                    Text(if(plan==null) "오늘은 어떤 동행인가요?" else "${profile.name}의 ${plan.purpose.label()}",style=RoadmateType.cardTitle)
                    if(plan==null) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            PetTravelPurpose.entries.forEach { purpose ->
                                FilterChip(data.purpose==purpose,onClick={viewModel.petAction { it.choosePurpose(purpose) }},
                                    label={Text(purpose.label())},modifier=Modifier.heightIn(min=56.dp))
                            }
                        }
                        Text(when(data.purpose) {
                            PetTravelPurpose.OUTING->"오늘 나들이에 필요한 물품을 직접 골라 확인해 주세요."
                            PetTravelPurpose.GROOMING->"맡길 물품과 전달할 내용을 출발 전에 확인해 주세요."
                            PetTravelPurpose.CLINIC->"보호자가 챙길 준비물과 물어볼 메모를 모아 보세요."
                        })
                    }
                    PetPackingItem.entries.forEach { item -> PetCheck(item.label(),item in data.packing) {
                        viewModel.petAction { it.togglePacking(item) }
                    } }
                    if(plan==null) {
                        GameButton("이번 동행 준비 시작",{viewModel.petAction { it.beginOuting() }},primary=true,enabled=state.historyEnabled)
                        if(!state.historyEnabled) {
                            Text("함께한 엽서를 만들려면 드라이브 기록 저장을 먼저 켜 주세요.")
                            GameButton("기록 저장 설정",openSettings)
                        } else Text("이후 시작하는 드라이브에 보호자가 선택한 동행을 연결해요.")
                    } else {
                        Text("정차 중, 보호자가 직접 확인해 주세요.",style=MaterialTheme.typography.titleMedium)
                        PetCheck("이동용품을 다시 챙겼어요",PetStopItem.BELONGINGS in plan.stopChecks) {
                            viewModel.petAction { it.toggleStop(PetStopItem.BELONGINGS) }
                        }
                        PetCheck("다음 일정을 확인했어요",PetStopItem.NEXT_SCHEDULE in plan.stopChecks) {
                            viewModel.petAction { it.toggleStop(PetStopItem.NEXT_SCHEDULE) }
                        }
                        if(state.completedTrip==null) {
                            Text("새 드라이브를 기다리고 있어요. 이동 후 완료된 기록이 있으면 펫 엽서를 만들 수 있어요.")
                            if(viewModel.canSimulateVisit) GameButton("지도에서 나들이 시연",openMap,primary=true)
                        }
                        PetOutingFinish(viewModel,state)
                        TextButton(onClick={confirmation="cancel"},modifier=Modifier.heightIn(min=56.dp)) { Text("이번 동행 준비 취소") }
                    }
                }
            },second={
                PetMemos(viewModel,data)
                RoadmateCard(compact=true) {
                    Text("우리 아이의 경험을 다음 동행으로",style=RoadmateType.cardTitle)
                    Text("준비·정차 확인은 보호자가 남긴 기록이에요. 정차만으로 도착이나 실제 휴식을 확정하지 않아요.")
                    Text("반려동물의 상태를 감지하거나 차량 설정을 바꾸지 않아요.",style=MaterialTheme.typography.bodySmall)
                }
            })
        }
        RoadmateCard(compact=true) {
            Text("이 기기의 펫 동행 기록",style=RoadmateType.cardTitle)
            Text("펫 프로필·준비·장소 메모는 별도로 보관해요. 원래 드라이브를 삭제하면 연결된 펫 엽서와 메모도 사라져요.")
            TextButton(onClick={confirmation="delete"},modifier=Modifier.heightIn(min=56.dp)) { Text("펫 데이터 전체 삭제") }
        }
    }
    if(editing) PetProfileDialog(viewModel,data.profile) { editing=false }
    confirmation?.let { action -> AlertDialog(onDismissRequest={confirmation=null},title={Text(when(action) {
        "delete"->"펫 데이터를 모두 삭제할까요?"; "mode-off"->"일반 동행으로 전환할까요?"; else->"이번 동행 준비를 취소할까요?"
    })},text={Text(if(action=="delete") "펫 프로필·장소 수첩·메모·펫 엽서·전용 보상을 지워요. 기존 여행·시즌·XP는 유지해요."
        else if(action=="mode-off") "진행 중인 펫 동행 준비와 휴식 타이머를 종료해요. 기존 드라이브와 저장한 펫 기록은 유지해요."
        else "아직 펫 엽서로 저장하지 않은 준비를 종료해요. 기존 드라이브와 저장한 펫 기록은 유지해요.")},
        confirmButton={GameButton(if(action=="delete") "펫 데이터 삭제" else "확인",{viewModel.petAction {
            val saved=when(action) { "delete"->it.deleteAll(); "mode-off"->it.setEnabled(false); else->it.cancelOuting() }
            if(saved) confirmation=null
        }},primary=true)},dismissButton={GameButton("돌아가기",{confirmation=null})}) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetProfileDialog(viewModel:RoadmateViewModel,profile:PetProfile?,close:()->Unit) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var kind by remember { mutableStateOf(profile?.kind ?: PetKind.DOG) }
    var consent by remember { mutableStateOf(profile!=null) }
    AlertDialog(onDismissRequest=close,title={Text(if(profile==null) "우리 아이 등록" else "우리 아이 프로필")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name,{if(it.length<=24 && it.none(Char::isISOControl)) name=it},label={Text("반려동물 이름")},singleLine=true,modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { PetKind.entries.forEach { value ->
                FilterChip(kind==value,{kind=value},label={Text(value.label())},modifier=Modifier.heightIn(min=56.dp))
            } }
            Text("화면 속 안내 동반자와 별도로, 실제 함께 타는 아이를 등록해요.")
            PetCheck("펫 프로필·준비·휴식 계획과 확인 기록·장소 메모·직접 저장한 펫 엽서를 이 기기에 보관하는 데 동의해요.",consent) { consent=!consent }
            Text("모드를 꺼도 기록은 유지해요. 펫 데이터 전체 삭제로 언제든 지울 수 있어요.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={GameButton("프로필 저장",{viewModel.petAction { if(it.saveProfile(name,kind,consent)) close() }},
        primary=true,enabled=consent && name.isNotBlank())},dismissButton={GameButton("취소",close)})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetMemos(viewModel:RoadmateViewModel,data:PetTravelData) {
    var text by remember { mutableStateOf("") }
    RoadmateCard {
        Text("다음 출발에도 기억해 줘",style=RoadmateType.cardTitle)
        Text("지난번에 직접 남긴 내용을 다음 준비에서 다시 확인해요.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            when(data.purpose) {
                PetTravelPurpose.OUTING->listOf("접이식 물그릇 챙기기","산책 가방 다시 확인")
                PetTravelPurpose.GROOMING->listOf("맡길 물품 다시 확인","전달할 내용 메모 챙기기")
                PetTravelPurpose.CLINIC->listOf("가져갈 자료 다시 확인","물어볼 질문 메모 챙기기")
            }.forEach { preset ->
                GameButton(preset,{viewModel.petAction { it.addMemo(preset) }})
            }
        }
        OutlinedTextField(text,{if(it.length<=180) text=it},label={Text("다음 출발 메모")},modifier=Modifier.fillMaxWidth(),maxLines=3)
        GameButton("출발 메모 저장",{viewModel.petAction { if(it.addMemo(text)) text="" }},enabled=text.isNotBlank())
        if(data.memos.isEmpty()) Text("기억할 준비 사항이 생기면 하나씩 남겨 주세요.")
        data.memos.forEach { memo ->
            PetCheck(memo.text,memo.checked) {viewModel.petAction {it.toggleMemo(memo.id)}}
            TextButton(onClick={viewModel.petAction {it.deleteMemo(memo.id)}},modifier=Modifier.heightIn(min=48.dp)) {Text("메모 삭제")}
        }
    }
}

@Composable
private fun PetOutingFinish(viewModel:RoadmateViewModel,state:PetTravelUiState) {
    val plan=state.data.plan ?: return
    var note by remember(plan.id) {mutableStateOf("")}
    var next by remember(plan.id) {mutableStateOf("")}
    if(state.completedTrip==null) return
    Text("함께한 드라이브가 준비됐어요",style=MaterialTheme.typography.titleMedium)
    Text("기록 시간 ${UiCopy.duration(requireNotNull(state.completedTrip).durationMillis)} · 보호자가 선택한 동행")
    OutlinedTextField(note,{if(it.length<=240) note=it},label={Text("오늘 동행의 한마디 · 선택")},modifier=Modifier.fillMaxWidth(),maxLines=3)
    OutlinedTextField(next,{if(it.length<=180) next=it},label={Text("다음 여행에 기억할 것 · 선택")},modifier=Modifier.fillMaxWidth(),maxLines=3)
    GameButton("펫 엽서로 저장",{viewModel.petAction {it.finishOuting(note,next)}},primary=true)
}

@Composable
internal fun PetPassContent(viewModel:RoadmateViewModel,openHub:()->Unit,openCards:()->Unit,openNormalPass:()->Unit) = PetGate(viewModel) { state ->
    RoadmateStoryHeader("첫 차 나들이 패스","함께 준비하고, 기억하고, 다음 여행으로 이어가요.","map-gangneung.png",1)
    if(!state.data.enabled) {
        RoadmateCard { Text("반려동물 동행을 켜면 상시 테마 패스를 이용할 수 있어요."); GameButton("펫 동행 열기",openHub,primary=true) }
    } else {
        val data=state.data
        RoadmateCard(tinted=true,compact=true) {
            Text("${data.profile?.name.orEmpty()}의 첫 차 나들이",style=RoadmateType.cardTitle)
            Text("상시 무료 테마 패스 · 기존 시즌과 함께 이용해요.")
            Text("${data.claimed.size} / 3 보상 수령")
        }
        val titles=listOf("오늘의 동행 준비","우리만의 나들이","다음에도 함께")
        val descriptions=listOf("우리 아이 프로필을 등록했어요.","완료된 드라이브를 펫 엽서로 직접 저장해요.","다음 출발에 볼 준비 메모를 하나 남겨요.")
        val rewards=listOf("동행 발자국 배지","발도장 프레임","별빛 프레임")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth>=980.dp) 3 else 1
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                PetPassReward.entries.chunked(columns).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach { reward -> RoadmateCard(Modifier.weight(1f)) {
                        val owned=reward in data.claimed; val ready=reward in state.availableRewards
                        PetPawMark(color=MaterialTheme.colorScheme.primary)
                        Text("${reward.ordinal+1}. ${titles[reward.ordinal]}",style=RoadmateType.cardTitle)
                        Text(descriptions[reward.ordinal])
                        Text(rewards[reward.ordinal],style=MaterialTheme.typography.titleMedium)
                        GameButton(if(owned) "수령 완료" else if(ready) "보상 받기" else "진행 중",
                            {viewModel.petAction {it.claim(reward)}},primary=ready,enabled=ready && !owned)
                        if(owned && reward!=PetPassReward.WELCOME_BADGE) {
                            val frame=if(reward==PetPassReward.PAW_FRAME) PetCardFrame.PAW else PetCardFrame.STAR
                            GameButton(if(data.frame==frame) "이 프레임 적용 중" else "엽서에 적용",{viewModel.petAction {it.applyFrame(frame)}},enabled=data.frame!=frame)
                        }
                    } }
                } }
            }
        }
        GameButton("동행 준비와 수첩",openHub)
        GameButton("함께한 펫 엽서 보기",openCards)
    }
    GameButton("기존 시즌 패스 보기",openNormalPass)
}

@Composable
internal fun PetCardsContent(viewModel:RoadmateViewModel,openHub:()->Unit)=PetGate(viewModel) { state ->
    var opened by remember {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf<String?>(null)}
    RoadmateStoryHeader("우리 아이와 함께한 엽서","보호자가 선택한 동행과 직접 남긴 이야기를 모아요.","drive-memory.png",2)
    if(!state.data.enabled) RoadmateCard {Text("반려동물 동행을 켜고 펫 엽서를 확인해 주세요.");GameButton("펫 동행 열기",openHub)}
    else if(state.cards.isEmpty()) RoadmateCard {
        Text("첫 동행의 한 장을 기다리고 있어요.",style=RoadmateType.cardTitle)
        Text("동행 준비 시작 후 완료된 드라이브를 직접 저장하면 여기에 모여요.")
        GameButton("동행 준비하러 가기",openHub,primary=true)
    } else state.cards.forEach { (card,trip) ->
        val frame=state.data.frame
        RoadmateCard {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if(frame==PetCardFrame.STAR) Color(0xFFEAE4F4) else Color(0xFFE0EFE5))
                .border(if(frame==PetCardFrame.PAPER) 1.dp else 3.dp,if(frame==PetCardFrame.STAR) Color(0xFF8070A7) else Color(0xFF659580),RoundedCornerShape(18.dp))
                .padding(20.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(frame==PetCardFrame.PAW) PetPawMark(color=Color(0xFF386F5C))
                        if(frame==PetCardFrame.STAR) Text("✦",color=Color(0xFF65518E),style=RoadmateType.heading)
                        Text("${card.petName}와 함께한 ${card.purpose.label()}",style=RoadmateType.cardTitle,color=GameInk)
                    }
                    Text("${UiCopy.date(trip.startedAtEpochMillis)} · UTC",color=GameInk)
                    Text("기록 시간 ${UiCopy.duration(trip.durationMillis)}",color=GameInk)
                    Text(if(trip.source==DataSource.SIMULATED) "시뮬레이션 동행 기록" else "보호자가 선택한 동행 기록",color=GameInk)
                    if(opened==card.tripId) {
                        Text(card.ownerNote.ifBlank {"함께한 순간을 한 장으로 간직했어요."},color=GameInk)
                        GameButton("엽서 닫기",{opened=null})
                    } else GameButton("펫 엽서 열기",{opened=card.tripId},primary=true)
                }
            }
            Text(state.data.frame.label(),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={deleting=card.tripId},modifier=Modifier.heightIn(min=56.dp)) {Text("이 펫 엽서 삭제")}
        }
    }
    deleting?.let { id -> AlertDialog(onDismissRequest={deleting=null},title={Text("이 펫 엽서를 삭제할까요?")},
        text={Text("연결된 다음 여행 메모도 지워요. 원래 드라이브 기록과 시즌·XP는 유지해요.")},
        confirmButton={GameButton("펫 엽서 삭제",{viewModel.petAction {if(it.deleteCard(id)) deleting=null}},primary=true)},
        dismissButton={GameButton("취소",{deleting=null})}) }
}

@Composable
internal fun PetPlacesContent(viewModel:RoadmateViewModel,openHub:()->Unit)=PetGate(viewModel) { state ->
    var editor by remember {mutableStateOf<PetPlaceNote?>(null)}
    var adding by remember {mutableStateOf(false)}
    RoadmateStoryHeader("우리 아이 장소 수첩","다녀본 경험도, 다음에 확인할 곳도 내 메모로 남겨요.","map-gangneung.png",1)
    if(!state.data.enabled) RoadmateCard {Text("반려동물 동행을 켜면 장소 수첩을 사용할 수 있어요.");GameButton("펫 동행 열기",openHub)}
    else {
        RoadmateCard(compact=true) {
            Text("보호자가 직접 남긴 장소",style=RoadmateType.cardTitle)
            Text("실제 방문 인증이나 동반 허용 정보가 아니에요. 입장 조건과 운영 정보는 방문 전에 직접 확인해 주세요.")
            GameButton("내 장소 추가",{adding=true;editor=null},primary=true)
        }
        if(state.data.places.isEmpty()) RoadmateCard {Text("다시 가고 싶은 곳이나 기억할 장소를 남겨 주세요.")}
        state.data.places.forEach { place -> RoadmateCard {
            Text(place.title,style=RoadmateType.cardTitle)
            RoadmateBadge(if(place.returnWanted) "다시 가고 싶은 곳" else "방문 전에 확인할 곳")
            if(place.note.isNotBlank()) Text(place.note)
            Text("보호자 메모",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                GameButton("장소 메모 수정",{editor=place;adding=true})
                TextButton(onClick={viewModel.petAction {it.deletePlace(place.id)}},modifier=Modifier.heightIn(min=56.dp)) {Text("장소 삭제")}
            }
        } }
    }
    if(adding) key(editor?.id) { PetPlaceDialog(viewModel,editor) {adding=false;editor=null} }
}

@Composable
private fun PetPlaceDialog(viewModel:RoadmateViewModel,place:PetPlaceNote?,close:()->Unit) {
    var title by remember {mutableStateOf(place?.title.orEmpty())}
    var note by remember {mutableStateOf(place?.note.orEmpty())}
    var wanted by remember {mutableStateOf(place?.returnWanted ?: true)}
    AlertDialog(onDismissRequest=close,title={Text(if(place==null) "내 장소 추가" else "장소 메모 수정")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{if(it.length<=60) title=it},label={Text("장소 이름 · 직접 입력")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(note,{if(it.length<=240) note=it},label={Text("우리 동행 메모")},modifier=Modifier.fillMaxWidth(),maxLines=4)
            PetCheck("다시 가고 싶은 곳으로 표시",wanted) {wanted=!wanted}
            Text("현재 위치나 공식 반려동물 동반 허용 정보로 등록되지 않아요.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={GameButton("장소 저장",{viewModel.petAction {if(it.savePlace(place?.id,title,note,wanted)) close()}},primary=true,enabled=title.isNotBlank())},
        dismissButton={GameButton("취소",close)})
}
