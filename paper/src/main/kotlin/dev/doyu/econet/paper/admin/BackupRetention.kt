package dev.doyu.econet.paper.admin

/** 백업 폴더 이름은 시각이라 이름순이 곧 시간순이다. 가장 최근 [keep]개를 남기고 나머지를 고른다. */
fun backupsToDelete(names: List<String>, keep: Int): List<String> {
    require(keep > 0) { "keep must be positive" }
    return names.sorted().dropLast(keep)
}
