package eu.alkismavridis.codescape.layout.model

import eu.alkismavridis.codescape.layout.calculations.calculateAbsoluteLeft
import eu.alkismavridis.codescape.layout.calculations.calculateAbsoluteTop

class MapArea(
  private val left: Double,
  private val top: Double,
  private val width: Double,
  private val height: Double,
  private val parent: MapArea?,
) {
  private val lazyAbsLeft by lazy { this.calculateAbsoluteLeft() }
  private val lazyAbsTop by lazy { this.calculateAbsoluteTop() }

  fun getLeft() = this.left
  fun getRight() = this.left + this.width
  fun getTop() = this.top
  fun getBottom() = this.top + this.height

  fun getCenterX() = this.left + this.width / 2
  fun getCenterY() = this.top + this.height / 2

  fun getAbsLeft() = this.lazyAbsLeft
  fun getAbsRight() = this.lazyAbsLeft + this.width

  fun getAbsTop() = this.lazyAbsTop
  fun getAbsBottom() = this.lazyAbsTop + this.height

  fun getWidth() = this.width
  fun getHeight() = this.height
  fun getParent() = this.parent
}
