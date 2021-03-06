package eu.alkismavridis.codescape.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.ScalableIcon
import eu.alkismavridis.codescape.config.StyleConfiguration
import eu.alkismavridis.codescape.tree.model.ChildrenLoadState
import eu.alkismavridis.codescape.tree.model.CodeScapeNode
import eu.alkismavridis.codescape.layout.calculations.intersectsWith
import eu.alkismavridis.codescape.layout.model.MapArea
import eu.alkismavridis.codescape.tree.actions.unloadChildren
import eu.alkismavridis.codescape.tree.model.NodeType
import eu.alkismavridis.codescape.tree.model.OpenState
import java.awt.*
import javax.swing.Icon
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class NodeRenderer(
  private val scale: Double,
  private val camera: MapArea,
  private val g: Graphics2D,
  private val styleConfig: StyleConfiguration,
  private val loadChildren: (node: CodeScapeNode) -> Unit,
  private val onAutoOpen: (node: CodeScapeNode) -> Unit,
  private val onAutoClose: (node: CodeScapeNode) -> Unit,
  private val imageProvider: ImageProvider,
) {
  fun render(node: CodeScapeNode) {
    if (this.camera.intersectsWith(node.area)) {
      renderVisibleNode(node)
    } else {
      this.onAutoClose(node)
    }
  }

  private fun renderVisibleNode(node: CodeScapeNode) {
    when(node.type) {
      NodeType.SIMPLE_BRANCH, NodeType.AUTO_LOADING_BRANCH -> renderVisibleDirectory(node)
      NodeType.LEAF -> renderVisibleFile(node)
      NodeType.LOCKED_BRANCH -> renderExplicitlyClosedDirectory(node)
    }
  }

  private fun renderVisibleDirectory(node: CodeScapeNode) {
    this.handleAutoOpenStatus(node)
    if (node.openState.isOpen) {
      renderOpenDirectory(node)
    } else {
      node.unloadChildren()
      renderClosedDirectory(node)
    }
  }

  private fun renderOpenDirectory(node: CodeScapeNode) {
    when(node.loadingState) {
      ChildrenLoadState.UNCHECKED -> {
        this.loadChildren(node)
        renderLoadingDirectory(node)
      }

      ChildrenLoadState.LOADING -> renderLoadingDirectory(node)
      ChildrenLoadState.LOADED -> renderOpenLoadedDirectory(node)
    }
  }

  private fun renderOpenLoadedDirectory(node: CodeScapeNode) {
    val area = node.area
    val image = node.options.imageId?.let { this.imageProvider.getImage(it) }
    if (image == null) {
      val x = area.getLeft().toPixelSpace(scale)
      val y = area.getTop().toPixelSpace(scale)
      val width = area.getWidth().toPixelSpace(scale)
      val height = area.getHeight().toPixelSpace(scale)

      this.g.color = node.options.openColor?.let { this.imageProvider.getColor(it) }
        ?: node.options.color?.let { this.imageProvider.getColor(it) }
        ?: this.imageProvider.getColor(this.styleConfig.openDirColor)
      this.g.fillRect(x, y, width, height)

      g.stroke = BasicStroke(node.options.borderWidth ?: this.styleConfig.borderWidth)
      this.g.color = node.options.borderColor
        ?.let { this.imageProvider.getColor(it) }
        ?: this.imageProvider.getColor(this.styleConfig.borderColor)
      this.g.drawRect(x, y, width, height)
    } else {
      renderImage(node.area, image)
    }

    renderNodeLabel(node)

    if (node.children.isEmpty()) return

    val translateX = area.getLeft().toPixelSpace(scale)
    val translateY = area.getTop().toPixelSpace(scale)

    this.g.translate(translateX, translateY)
    node.children.forEach { render(it) }
    this.g.translate(-translateX, -translateY)
  }

  private fun renderLoadingDirectory(node: CodeScapeNode) = renderSolidNode(node, this.styleConfig.loadingDirColor, AllIcons.General.Ellipsis)
  private fun renderVisibleFile(node: CodeScapeNode) = renderSolidNode(node, this.styleConfig.fileColor, AllIcons.Actions.Checked)
  private fun renderExplicitlyClosedDirectory(node: CodeScapeNode) = renderSolidNode(node, this.styleConfig.lockedDirColor, AllIcons.Process.Stop)
  private fun renderClosedDirectory(node: CodeScapeNode) = renderSolidNode(node, this.styleConfig.closedDirColor, AllIcons.Nodes.Folder)

  private fun renderNodeLabel(node: CodeScapeNode) {
    val area = node.area
    val widthPx = area.getWidth().toPixelSpace(this.scale)
    if (widthPx > SHOW_LABEL_THRESHOLD) {
      val labelXPixel = area.getLeft().toPixelSpace(this.scale)
      val labelHeightPx = min((area.getHeight() * 0.15).toPixelSpace(this.scale), 30)
      val labelYPixel = area.getTop().toPixelSpace(this.scale)
      val fontSize = min(widthPx / 10.0, 16.0).roundToInt()

      this.g.color = this.imageProvider.getColor(this.styleConfig.labelBackground)
      this.g.fillRect(labelXPixel, labelYPixel, widthPx, labelHeightPx)

      this.g.font = Font("serif", Font.PLAIN, fontSize)
      val fm = this.g.fontMetrics

      val originalClip = this.g.clip
      this.g.color = this.imageProvider.getColor(this.styleConfig.labelColor)
      this.g.clip = Rectangle(labelXPixel, labelYPixel, widthPx, labelHeightPx)
      this.g.drawString(node.label, labelXPixel + 4, labelYPixel + (fm.ascent + labelHeightPx) / 2)
      this.g.clip = originalClip
    }
  }

  private fun renderSolidNode(node: CodeScapeNode, defaultColor: String, icon: Icon?) {
    val image = node.options.imageId?.let { this.imageProvider.getImage(it) }
    val area = node.area

    val leftPx = area.getLeft().toPixelSpace(scale)
    val topPx = area.getTop().toPixelSpace(scale)
    val widthPx = area.getWidth().toPixelSpace(scale)
    val heightPx = area.getHeight().toPixelSpace(scale)

    if (image == null) {
      this.g.color = node.options.color
        ?.let{ this.imageProvider.getColor(it) }
        ?: this.imageProvider.getColor(defaultColor)
      this.g.fillRect(leftPx, topPx, widthPx, heightPx)
    } else {
      renderImage(node.area, image)
    }

    g.stroke = BasicStroke(node.options.borderWidth ?: this.styleConfig.borderWidth)
    g.color = node.options.borderColor
      ?.let { this.imageProvider.getColor(it) }
      ?: this.imageProvider.getColor(this.styleConfig.borderColor)
    this.g.drawRect(leftPx, topPx, widthPx, heightPx)

    if (icon != null && !node.options.hideIcon) {
      this.renderIcon(icon, leftPx, topPx, widthPx, heightPx)
    }
    renderNodeLabel(node)
  }

  private fun renderImage(area: MapArea, image: Image) {
    val nodeLeft = area.getLeft().toPixelSpace(scale)
    val nodeTop = area.getTop().toPixelSpace(scale)
    val nodeWidth = area.getWidth().toPixelSpace(scale)
    val nodeHeight = area.getHeight().toPixelSpace(scale)
    val imageWidth = image.getWidth(null)
    val imageHeight = image.getHeight(null)

    val aspectRatio = min(imageWidth.toDouble() / nodeWidth, imageHeight.toDouble()  / nodeHeight)
    val clipLeft = ((imageWidth - aspectRatio * nodeWidth) / 2).toInt()
    val clipTop = ((imageHeight - aspectRatio * nodeHeight) / 2).toInt()
    val clipWidth = (aspectRatio * nodeWidth).toInt()
    val clipHeight = (aspectRatio * nodeHeight).toInt()
    this.g.drawImage(
      image,
      nodeLeft,
      nodeTop,
      nodeLeft + nodeWidth,
      nodeTop + nodeHeight,
      clipLeft,
      clipTop,
      clipLeft + clipWidth,
      clipTop + clipHeight,
      null
    )
  }

  private fun renderIcon(icon: Icon, nodeLeft: Int, nodeTop: Int, nodeWidth: Int, nodeHeight: Int) {
    if (icon is ScalableIcon) {
      val scaleFactor = nodeWidth / icon.iconHeight * 0.618033988
      val scaled = icon.scale(scaleFactor.toFloat())
      scaled.paintIcon(null, this.g, nodeLeft + nodeWidth / 2 - scaled.iconWidth / 2, nodeTop + nodeHeight / 2 - scaled.iconHeight / 2)
    }
  }

  private fun handleAutoOpenStatus(node: CodeScapeNode) {
    when(this.getAutoOpenStatus(node)) {
      AutoOpenStatus.AUTO_OPEN -> this.onAutoOpen(node)
      AutoOpenStatus.AUTO_CLOSE -> this.onAutoClose(node)
      else -> {}
    }
  }

  private fun getAutoOpenStatus(node: CodeScapeNode) : AutoOpenStatus {
    val widthPx = node.area.getWidth() * this.scale
    val heightPx = node.area.getHeight() * this.scale
    val maxDimension = max(widthPx, heightPx)
    val openingThreshold = if(node.openState == OpenState.EXPLICITLY_CLOSED) this.styleConfig.hardOpenDirPx else this.styleConfig.autoOpenDirPx
    val closingThreshold = if(node.openState == OpenState.EXPLICITLY_OPEN) this.styleConfig.hardCloseDirPx else this.styleConfig.autoCloseDirPx

    return if(openingThreshold > 0 && maxDimension > openingThreshold) AutoOpenStatus.AUTO_OPEN
    else if (closingThreshold > 0 && maxDimension < closingThreshold) AutoOpenStatus.AUTO_CLOSE
    else AutoOpenStatus.NO_ACTION
  }

  private fun Double.toPixelSpace(scale: Double): Int {
    return (this * scale).roundToInt()
  }

  private enum class AutoOpenStatus { AUTO_OPEN, AUTO_CLOSE, NO_ACTION }

  companion object {
    private const val SHOW_LABEL_THRESHOLD = 60
  }
}
