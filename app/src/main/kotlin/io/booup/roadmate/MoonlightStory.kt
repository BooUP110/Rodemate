package io.booup.roadmate

internal enum class MoonlightChapter(val title: String, val art: String, val introduction: String, val arrival: String,
    val resident: String, val clue: String, val mission: String, val puzzle: String,
    val options: List<String>, val answer: Int, val discovery: String) {
    OJUKHEON("빈 엽서", "story-gangneung-ojukheon.png",
        "이 엽서 좀 봐. ‘강릉을 떠나는 친구에게’라고 쓰여 있는데, 그림이 하나도 없어. 오죽헌의 엽서지기에게 물어볼까?",
        "여기가 오죽헌이구나. 저기 마당에 엽서지기가 있어. 같이 가 볼까?",
        "오죽헌의 엽서지기", "그 친구는 이 마당의 나무를 좋아했어. 첫 조각에 잎이 무성한 가지를 그려 뒀단다.",
        "오죽헌 마당을 둘러보고 도착을 확인해 주세요.",
        "엽서지기가 첫 조각에 뭘 그렸더라?", listOf("잎이 달린 가지", "바다 조개", "둥근 돌"), 0,
        "조각을 붙였더니 글씨가 나타났어. ‘하늘에도 하나, 물 위에도 하나.’ 물이 있는 경포대로 가 보자."),
    GYEONGPODAE("두 개의 달", "story-gangneung-gyeongpodae.png",
        "하늘에 하나, 물 위에 하나. 경포대에 가면 그게 뭔지 알 수 있겠지?",
        "호수가 정말 잔잔하다. 저기 물가를 걷는 친구한테 물어보자.",
        "경포대의 산책 친구", "그 친구랑 여기서 달을 본 적 있어. 바람이 불면 물 위에 비친 달도 같이 일렁였지.",
        "경포대에 도착하면 호수 풍경을 둘러보고 도착을 확인해 주세요.",
        "산책 친구가 본 달은 어디에 있었을까?", listOf("구름과 달", "물 위에 비친 달", "별과 달"), 1,
        "달 그림 옆에 파도가 나타났어. 여기 ‘강문’이라고 쓰여 있네. 이번엔 바다로 가자."),
    GANGMUN("파도가 지운 이름", "story-gangneung-gangmun.png",
        "파도 아래 이름이 반쯤 지워졌어. 강문해변에선 이 편지를 본 친구가 있을까?",
        "파도 소리가 여기까지 들려. 저기 편지를 들고 있는 친구에게 물어보자.",
        "강문해변의 편지 배달부", "그 편지 기억나. 받는 사람 이름 옆에 파도가 먼저, 그다음에 달이 그려져 있었어.",
        "강문해변 산책로에서 바다를 보고 도착을 확인해 주세요.",
        "배달부는 어떤 그림이 먼저였다고 했지?", listOf("달 → 가지", "가지 → 파도", "파도 → 달"), 2,
        "이제 이름이 보여. 강릉을 떠나는 친구한테 함께 본 풍경을 선물하려던 거였어. 안목에서 기다리고 있대."),
    ANMOK("안목에 닿은 편지", "story-gangneung-anmok.png",
        "이제 안목에 가면 친구를 만날 수 있어. 엽서 조각은 다 챙겼지?",
        "저기 바다를 보고 있는 친구가 보여. 얼른 엽서를 보여 주자.",
        "엽서를 기다리던 친구", "이 길을 한 번 더 걷고 싶었어. 우리가 어디서부터 걸었는지 순서대로 붙여 줄래?",
        "안목해변 산책로에 도착하면 마지막 바다 풍경을 보고 도착을 확인해 주세요.",
        "우리가 들른 곳을 순서대로 골라 보자.", listOf("오죽헌 → 경포대 → 강문 → 안목", "경포대 → 안목 → 오죽헌 → 강문", "안목 → 강문 → 경포대 → 오죽헌"), 0,
        "고마워. 강릉이 그리울 때마다 이 엽서를 꺼내 볼게. 다음엔 내가 엽서 쓸게!"),
    ;

    val place get() = Expedition.GANGNEUNG.places[ordinal]
}

internal enum class MoonlightOutfit(val label: String, val chapter: Int, val premium: Boolean) {
    SCARF("대나무빛 스카프", 1, false), HAT("달빛 여행 모자", 2, true),
    JACKET("파도 바람막이", 3, false), CAPE("별빛 망토", 4, true),
    ;

    val claimKey get() = "moonlight:outfit:$name"
}

internal const val MOONLIGHT_DEMO_PREMIUM = "moonlight:premium-demo"
