package dev.doyu.econet.paper.admin

/** 다 끝난 백업의 폴더 이름. 쓰는 중인 폴더는 이 이름이 아니므로 보존 개수에 세지 않는다. */
val COMPLETED_BACKUP_NAME = Regex("""\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}Z""")

/** 백업 폴더 이름은 시각이라 이름순이 곧 시간순이다. 완료된 백업만 세어 가장 최근 [keep]개를 남기고 나머지를 고른다. */
fun backupsToDelete(names: List<String>, keep: Int): List<String> {
    require(keep > 0) { "keep must be positive" }
    return names.filter(COMPLETED_BACKUP_NAME::matches).sorted().dropLast(keep)
}
