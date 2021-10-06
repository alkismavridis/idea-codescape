package eu.alkismavridis.codescape.fs

import eu.alkismavridis.codescape.config.CodeScapeConfigurationService
import eu.alkismavridis.codescape.config.NodeVisibility
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class NioFsService(
  private val configService: CodeScapeConfigurationService,
  private val projectRoot: Path,
): FsService {

  override fun getChildrenOf(path: String): Sequence<FileNode> {
    val dirPath = FileSystems.getDefault().getPath(path)

    return Files.walk(this.projectRoot.resolve(dirPath), 1)
      .skip(1)
      .map(this::toFileNode)
      .filter { it.options.visibility != NodeVisibility.HIDDEN }
      .iterator()
      .asSequence()
  }

  override fun loadContentsOf(path: String): InputStream {
    return Files.newInputStream(this.projectRoot.resolve(path))
  }

  private fun toFileNode(path: Path): FileNode {
    val projectPath = projectRoot.relativize(path).toString()
    val nodeOptions = this.configService.getOptionsFor(projectPath)
    return FileNode(path.fileName.toString(), projectPath, Files.isDirectory(path), nodeOptions)
  }
}