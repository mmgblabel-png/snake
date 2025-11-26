@file:Suppress("MagicNumber")
package com.mmgb.snake

import kotlin.math.roundToLong

// Public core grid and helpers shared with tests
const val COLS = 22
const val ROWS = 22

// Tick speeds for indices 0..4 (ms per move) classic mode
private val SPEED_LEVELS = longArrayOf(180L, 140L, 110L, 85L, 65L)
private const val FAST_MULTIPLIER = 0.6f
private const val SLOW_MULTIPLIER = 1.55f

data class Cell(var x: Int, var y: Int)

enum class PowerUp { GHOST, FAST, SLOW, DOUBLE, PHASE }

fun isEffectActive(effectUntil: Map<PowerUp, Long>, k: PowerUp): Boolean =
  System.currentTimeMillis() < (effectUntil[k] ?: 0L)

fun currentInterval(speedIdx: Int, effectUntil: Map<PowerUp, Long>): Long {
  val idx = speedIdx.coerceIn(0, SPEED_LEVELS.lastIndex)
  val base = SPEED_LEVELS[idx]
  var v = base.toFloat()
  if (isEffectActive(effectUntil, PowerUp.FAST)) v *= FAST_MULTIPLIER // FAST makes snake quicker
  if (isEffectActive(effectUntil, PowerUp.SLOW)) v *= SLOW_MULTIPLIER // SLOW makes snake slower
  return v.roundToLong().coerceIn(40L, 420L)
}

fun randCell(exclude: Set<Cell> = emptySet()): Cell {
  val rnd = kotlin.random.Random
  while (true) {
    val x = rnd.nextInt(COLS)
    val y = rnd.nextInt(ROWS)
    if (exclude.none { it.x == x && it.y == y }) return Cell(x, y)
  }
}

fun nudge(curr: Cell, nd: Cell, cd: Cell): Cell =
  if (cd.x + nd.x == 0 && cd.y + nd.y == 0) curr else nd
