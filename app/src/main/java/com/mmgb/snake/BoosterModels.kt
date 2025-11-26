@file:Suppress("MaxLineLength")
package com.mmgb.snake

// Shared booster model definitions used by game logic and tests

enum class BoosterKind { START_LENGTH, SCORE_MULT, EXTRA_TIME }

enum class SuperBoosterKind { SHIELD }

data class BoosterDef(
  val id: String,
  val title: String,
  val description: String,
  val price: Int,
  val isSuper: Boolean,
  val kind: BoosterKind? = null,
  val superKind: SuperBoosterKind? = null
)

val BOOSTER_CATALOG: List<BoosterDef> = listOf(
  BoosterDef(id = "booster_start_length", title = "Start+3", description = "Begin met een langere slang", price = 75, isSuper = false, kind = BoosterKind.START_LENGTH),
  BoosterDef(id = "booster_score_mult", title = "+25% Score", description = "Verdien 25% extra punten", price = 90, isSuper = false, kind = BoosterKind.SCORE_MULT),
  BoosterDef(id = "booster_extra_time", title = "+20s Tijd", description = "Extra tijd in Time-Attack", price = 80, isSuper = false, kind = BoosterKind.EXTRA_TIME),
  BoosterDef(id = "super_shield", title = "Schild", description = "Negeer een botsing", price = 250, isSuper = true, superKind = SuperBoosterKind.SHIELD)
)

fun boosterCatalog(): List<BoosterDef> = BOOSTER_CATALOG
