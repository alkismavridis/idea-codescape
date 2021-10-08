package eu.alkismavridis.codescape.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.ScalableIcon
import eu.alkismavridis.codescape.tree.model.ChildrenLoadState
import eu.alkismavridis.codescape.tree.model.CodeScapeNode
import eu.alkismavridis.codescape.layout.calculations.intersectsWith
import eu.alkismavridis.codescape.layout.model.MapArea
import eu.alkismavridis.codescape.tree.actions.unloadChildren
import eu.alkismavridis.codescape.tree.model.NodeType
import org.jetbrains.rpc.LOG
import java.awt.*
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.math.min
import kotlin.math.roundToInt


class NodeRenderer(
  private val scale: Double,
  private val camera: MapArea,
  private val g: Graphics2D,
  private val loadChildren: (node: CodeScapeNode) -> Unit,
  private val imageProvider: ImageProvider,
) {
  fun render(node: CodeScapeNode) {
    if (this.camera.intersectsWith(node.area)) {
      renderVisibleNode(node)
    } else {
      node.isOpen = false
      node.unloadChildren()
    }
  }

  private fun renderVisibleNode(node: CodeScapeNode) {
    when(node.type) {
      NodeType.BRANCH -> renderVisibleDirectory(node)
      NodeType.LOCKED_BRANCH -> renderExplicitlyClosedDirectory(node)
      NodeType.LEAF -> renderVisibleFile(node)
    }
  }

  private fun renderVisibleDirectory(node: CodeScapeNode) {
    val widthPx = node.area.getWidth() * this.scale
    val heightPx = node.area.getHeight() * this.scale
    node.isOpen = widthPx > OPEN_DIR_THRESHOLD || heightPx > OPEN_DIR_THRESHOLD

    if (node.isOpen) {
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
        ?: OPEN_DIR_BACKGROUND
      this.g.fillRect(x, y, width, height)

      g.stroke = node.options.borderWidth?.let { BasicStroke(it) } ?: BORDER_STROKE
      this.g.color = node.options.borderColor?.let { this.imageProvider.getColor(it) } ?: BORDER_COLOR
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

  private fun renderLoadingDirectory(node: CodeScapeNode) = renderSolidNode(node, LOADING_DIR_BACKGROUND, AllIcons.General.Ellipsis)
  private fun renderVisibleFile(node: CodeScapeNode) = renderSolidNode(node, FILE_BACKGROUND, null)
  private fun renderExplicitlyClosedDirectory(node: CodeScapeNode) = renderSolidNode(node, EXPLICITLY_CLOSED_BACKGROUND, AllIcons.Process.Stop)
  private fun renderClosedDirectory(node: CodeScapeNode) = renderSolidNode(node, CLOSED_DIR_BACKGROUND, AllIcons.Nodes.Folder)

  private fun renderNodeLabel(node: CodeScapeNode) {
    val area = node.area
    val widthPx = area.getWidth() * this.scale
    if (widthPx > SHOW_LABEL_THRESHOLD) {
      val nodeXPixel = area.getLeft().toPixelSpace(this.scale)
      val nodeYPixel = area.getTop().toPixelSpace(this.scale)
      val fontSize = min(widthPx / 10, 20.0).roundToInt()

      val originalClip = this.g.clip
      this.g.clip = Rectangle(nodeXPixel, nodeYPixel - 26, area.getWidth().toPixelSpace(this.scale), 30)

      this.g.font = Font("serif", Font.PLAIN, fontSize)
      val fm = this.g.fontMetrics
      val rect = fm.getStringBounds(node.label, this.g)

      this.g.color = LABEL_BACKGROUND
      this.g.fillRect(nodeXPixel - 4, nodeYPixel - fm.ascent, rect.width.roundToInt() + 8, rect.height.roundToInt())

      this.g.color = LABEL_COLOR
      this.g.drawString(node.label, nodeXPixel, nodeYPixel)

      this.g.clip = originalClip
    }
  }

  private fun renderSolidNode(node: CodeScapeNode, defaultColor: Color, icon: Icon?) {
    val image = node.options.imageId?.let { this.imageProvider.getImage(it) }
    val area = node.area

    val leftPx = area.getLeft().toPixelSpace(scale)
    val topPx = area.getTop().toPixelSpace(scale)
    val widthPx = area.getWidth().toPixelSpace(scale)
    val heightPx = area.getHeight().toPixelSpace(scale)

    if (image == null) {
      this.g.color = node.options.color?.let{ this.imageProvider.getColor(it) } ?: defaultColor
      this.g.fillRect(leftPx, topPx, widthPx, heightPx)
    } else {
      renderImage(node.area, image)
    }

    g.stroke = node.options.borderWidth?.let { BasicStroke(it) } ?: BORDER_STROKE
    g.color = node.options.borderColor
      ?.let { this.imageProvider.getColor(it) }
      ?: BORDER_COLOR
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

  private fun Double.toPixelSpace(scale: Double): Int {
    return (this * scale).roundToInt()
  }

  companion object {
    private const val OPEN_DIR_THRESHOLD = 200
    private const val SHOW_LABEL_THRESHOLD = 60

    private val OPEN_DIR_BACKGROUND = Color(192, 192, 192)
    private val LOADING_DIR_BACKGROUND = Color.GRAY
    private val EXPLICITLY_CLOSED_BACKGROUND = Color(148, 2, 2)
    private val CLOSED_DIR_BACKGROUND = Color(175, 150, 3)
    private val FILE_BACKGROUND = Color(0, 120, 0)
    private val LABEL_COLOR = Color(0,0, 0)
    private val LABEL_BACKGROUND = Color(200,200, 200)
    private val BORDER_COLOR = Color.BLACK
    private val BORDER_STROKE = BasicStroke(2f)
  }
}
