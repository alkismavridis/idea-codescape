package eu.alkismavridis.codescape.tree

import eu.alkismavridis.codescape.config.CodeScapeConfigurationService
import eu.alkismavridis.codescape.config.NodeOptions
import eu.alkismavridis.codescape.config.NodeVisibility
import eu.alkismavridis.codescape.layout.LayoutService
import eu.alkismavridis.codescape.layout.model.MapArea
import eu.alkismavridis.codescape.tree.model.ChildrenLoadState
import eu.alkismavridis.codescape.tree.model.CodeScapeNode
import eu.alkismavridis.codescape.tree.model.NodeType
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors.toList

class NioTreeDataService(
  private val configService: CodeScapeConfigurationService,
  private val layoutService: LayoutService,
  private val projectRoot: Path,
): TreeDataService {

  override fun loadChildren(parent: CodeScapeNode, onPresent: () -> Unit) {
    if (parent.loadingState != ChildrenLoadState.UNCHECKED) {
      return
    }

    parent.loadingState = ChildrenLoadState.LOADING
    onPresent()

    val files = this.getFiles(parent.id)
    if (files.size > SIZE_LIMIT) {
      parent.loadingState = ChildrenLoadState.LOADED
      parent.type = NodeType.LOCKED_BRANCH
      onPresent()
    } else {
      parent.children = this.layoutService
        .layout(parent.area, files) { file, area -> this.createNode(file, area) }
        .toList()

      parent.loadingState = ChildrenLoadState.LOADED
      onPresent()
    }
  }

  override fun loadContentsOf(path: String): InputStream {
    return Files.newInputStream(this.projectRoot.resolve(path))
  }

  private fun getFiles(parentPath: String) : List<FileData> {
    return Files.walk(this.projectRoot.resolve(parentPath), 1)
      .skip(1)
      .map(this::toFileNode)
      .filter { it.options.visibility != NodeVisibility.HIDDEN }
      .limit(SIZE_LIMIT + 1L)
      .collect(toList())
  }

  private fun toFileNode(path: Path): FileData {
    val projectPath = projectRoot.relativize(path).toString()
    val nodeOptions = this.configService.getOptionsFor(projectPath)
    return FileData(path.fileName.toString(), projectPath, Files.isDirectory(path), nodeOptions)
  }

  private fun createNode(fileData: FileData, area: MapArea) : CodeScapeNode {
    val nodeType = when {
      !fileData.isDirectory -> NodeType.LEAF
      fileData.options.visibility == NodeVisibility.CLOSED -> NodeType.LOCKED_BRANCH
      else -> NodeType.BRANCH
    }

    val childLoadState = if(nodeType == NodeType.BRANCH) {
      ChildrenLoadState.UNCHECKED
    } else {
      ChildrenLoadState.LOADED
    }

    return CodeScapeNode(fileData.path, fileData.name, fileData.options.image, nodeType, area, emptyList(), childLoadState)
  }

  private class FileData(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val options: NodeOptions
  )

  companion object {
    private const val SIZE_LIMIT = 100
  }
}