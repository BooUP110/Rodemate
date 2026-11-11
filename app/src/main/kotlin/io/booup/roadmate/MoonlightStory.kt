package io.booup.roadmate

internal enum class MoonlightChapter(val title: String, val art: String, val introduction: String,
    val resident: String, val clue: String, val mission: String, val puzzle: String,
    val options: List<String>, val answer: Int, val discovery: String) {
    OJUKHEON("기 · 빈 엽서", "story-gangneung-ojukheon.png",
        "오죽헌에 빈 엽서가 떨어져 있었어. 뒷면에 '강릉을 떠나는 친구에게'라고 쓰여 있어.",
        "오죽헌의 엽서지기", "그 친구가 좋아하던 길을 따라가 보렴. 내가 그려 둔 첫 조각은 여러 잎이 달린 가지야.",
        "오죽헌에 도착해 공개 관람 구역의 풍경을 살펴보고 위치를 인증하세요.",
        "엽서지기가 말한 첫 그림 조각을 골라 주세요.", listOf("잎이 달린 가지", "바다 조개", "둥근 돌"), 0,
        "첫 조각을 붙이니 '하늘에도 하나, 물 위에도 하나'라는 문장이 나타났어. 경포대에 가 보자!"),
    GYEONGPODAE("승 · 두 개의 달", "story-gangneung-gyeongpodae.png",
        "오죽헌에서 찾은 문장 기억나? 하늘에 하나, 물 위에 하나. 달이 두 개라는 게 무슨 뜻일까?",
        "경포대의 산책 친구", "그 친구와 함께 달을 보던 밤엔, 물결이 일 때마다 달빛도 같이 흔들렸어.",
        "경포대에 도착해 물이 보이는 공개 장소에서 풍경을 관찰하고 위치를 인증하세요.",
        "엽서지기가 본 풍경과 같은 그림 조각을 골라 주세요.", listOf("구름과 달", "물 위에 비친 달", "별과 달"), 1,
        "달빛 조각을 붙이니 파도 그림 옆에 '강문'이라는 글자가 보였어. 강문해변에 다음 단서가 있겠어."),
    GANGMUN("전 · 파도가 지운 이름", "story-gangneung-gangmun.png",
        "파도 그림 아래에 글자가 반쯤 지워져 있어. 엽서를 받기로 했던 친구는 누구였을까?",
        "강문해변의 편지 배달부", "친구가 마지막으로 남긴 편지는 파도 그림 옆에 달이 그려져 있었어. 엽서에 그 순서를 남겨 줘.",
        "강문해변의 공개 산책 구역에 도착해 바다를 보고 위치를 인증하세요.",
        "편지 배달부가 기억한 그림 순서를 골라 주세요.", listOf("달 → 가지", "가지 → 파도", "파도 → 달"), 2,
        "이제 읽을 수 있어. 이 엽서는 강릉을 떠나는 친구에게 우리가 함께 본 풍경을 남겨 주려고 만든 거였어. 안목에서 건네자!"),
    ANMOK("결 · 안목에 닿은 편지", "story-gangneung-anmok.png",
        "안목에 도착하면 완성한 엽서를 기다리는 친구에게 건네자. 모은 조각은 모두 챙겼지?",
        "엽서를 기다리던 친구", "다시 걸어 보고 싶은 길을 순서대로 담아 주면 좋겠어. 어디서부터 출발했더라?",
        "안목해변의 공개 산책 구역에 도착해 마지막 여행 풍경을 보고 위치를 인증하세요.",
        "네가 방문한 순서로 엽서를 완성해 주세요.", listOf("오죽헌 → 경포대 → 강문 → 안목", "경포대 → 안목 → 오죽헌 → 강문", "안목 → 강문 → 경포대 → 오죽헌"), 0,
        "고마워. 덕분에 강릉을 떠나도 이 길을 잊지 않을 거야. 다음에는 내가 너에게 엽서를 보낼게."),
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
