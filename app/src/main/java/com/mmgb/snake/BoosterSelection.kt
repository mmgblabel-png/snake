package com.mmgb.snake

private const val MAX_STANDARD_BOOSTERS_PER_RUN = 3
private const val MAX_SUPER_BOOSTERS_PER_RUN = 1

/** Booster selection rules: max 3 standard boosters and max 1 super booster per run. */
fun canSelectBooster(selected: Set<String>, def: BoosterDef, catalog: List<BoosterDef>): Boolean {
    val selectedDefs = selected.mapNotNull { id -> catalog.find { it.id == id } }
    val standardCount = selectedDefs.count { !it.isSuper }
    val superCount = selectedDefs.count { it.isSuper }
    return if (def.isSuper) {
        (def.id in selected) || superCount < MAX_SUPER_BOOSTERS_PER_RUN
    } else {
        (def.id in selected) || standardCount < MAX_STANDARD_BOOSTERS_PER_RUN
    }
}
