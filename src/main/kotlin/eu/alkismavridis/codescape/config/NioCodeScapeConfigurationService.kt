package eu.alkismavridis.codescape.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.rpc.LOG
import java.nio.file.Files
import java.nio.file.Path

class NioCodeScapeConfigurationService(private val projectRoot: Path) : CodeScapeConfigurationService {
  private val config by lazy { this.loadConfiguration() }
  private val absoluteRootPath = projectRoot.toAbsolutePath().toString()

  override fun getOptionsFor(absolutePath: String): NodeOptions {
    val rule = this.config.rules.find {
      val projectRelativePath = absolutePath.removePrefix(absoluteRootPath)
      it.compiledRegex.matches(projectRelativePath)
    }

    LOG.info("absoluteRootPath: $absoluteRootPath\n absolutePath: $absolutePath\n rel: ${absolutePath.removePrefix(absoluteRootPath)}\n rule found: ${rule != null}")


    return NodeOptions(
      rule?.visibility ?: NodeVisibility.VISIBLE
    )
  }

  private fun loadConfiguration(): CodeScapeConfiguration {
    val configStream = this.projectRoot
      .resolve("codescape-config.json")
      .takeIf { Files.exists(it) }
      ?.let { Files.newInputStream(it) }
      ?: this::class.java.classLoader.getResourceAsStream("codescape-config.json")
      ?: throw IllegalStateException("No config file found")

    val config = ObjectMapper().registerKotlinModule().readValue<CodeScapeConfiguration>(configStream)
    LOG.info("Loaded config " + config.rules.size)
    return config
  }
}
