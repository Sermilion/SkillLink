package skilllink.domain.source

import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.events.CollectionEndEvent
import org.snakeyaml.engine.v2.events.CollectionStartEvent
import org.snakeyaml.engine.v2.events.Event
import org.snakeyaml.engine.v2.events.NodeEvent
import org.snakeyaml.engine.v2.events.ScalarEvent
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.schema.FailsafeSchema

object FrontmatterReader {
  private const val MAX_HEADER_LENGTH = 65_536
  private const val MAX_NESTING_DEPTH = 64
  private const val NAME = "name"
  private const val DESCRIPTION = "description"
  private val settings =
    LoadSettings
      .builder()
      .setSchema(FailsafeSchema())
      .setCodePointLimit(MAX_HEADER_LENGTH)
      .setMaxAliasesForCollections(0)
      .build()

  fun parse(content: String): FrontmatterOutcome {
    val lines = content.removePrefix("\uFEFF").lineSequence().iterator()
    if (!lines.hasNext() || lines.next().trimEnd() != "---") {
      return FrontmatterOutcome.Rejected.MissingDelimiter
    }
    val block = StringBuilder()
    var outcome: FrontmatterOutcome? = null
    while (lines.hasNext() && outcome == null) {
      val line = lines.next()
      outcome =
        when {
          line.trimEnd() == "---" -> {
            parseYaml(block.toString())
          }

          block.length + line.length + 1 > MAX_HEADER_LENGTH -> {
            FrontmatterOutcome.Rejected.InputLimit
          }

          else -> {
            block.append(line).append('\n')
            null
          }
        }
    }
    return outcome ?: FrontmatterOutcome.Rejected.MissingDelimiter
  }

  private fun parseYaml(block: String): FrontmatterOutcome =
    try {
      val rejected = inspectEvents(block)
      if (rejected != null) {
        rejected
      } else {
        val root = Compose(settings).composeString(block).orElse(null)
        if (root !is MappingNode) {
          FrontmatterOutcome.Rejected.Malformed
        } else {
          validateMapping(root, topLevel = true) ?: readFields(root)
        }
      }
    } catch (_: YamlEngineException) {
      FrontmatterOutcome.Rejected.Malformed
    }

  private fun inspectEvents(block: String): FrontmatterOutcome.Rejected? {
    var depth = 0
    var rejected: FrontmatterOutcome.Rejected? = null
    val events = Parse(settings).parseString(block).iterator()
    while (events.hasNext() && rejected == null) {
      val event = events.next()
      when (event) {
        is CollectionStartEvent -> depth++
        is CollectionEndEvent -> depth--
      }
      rejected =
        when {
          isUnsafeEvent(event) -> FrontmatterOutcome.Rejected.UnsafeConstruct
          depth > MAX_NESTING_DEPTH -> FrontmatterOutcome.Rejected.InputLimit
          else -> null
        }
    }
    return rejected
  }

  private fun isUnsafeEvent(event: Event): Boolean {
    val tag =
      when (event) {
        is ScalarEvent -> event.tag
        is CollectionStartEvent -> event.tag
        else -> null
      }
    return (event is NodeEvent && event.anchor.isPresent) || tag?.isPresent == true
  }

  private fun validateMapping(
    mapping: MappingNode,
    topLevel: Boolean,
  ): FrontmatterOutcome.Rejected? {
    val keys = mutableSetOf<String>()
    return mapping.value.firstNotNullOfOrNull { entry ->
      val key = entry.keyNode as? ScalarNode
      when {
        key == null -> FrontmatterOutcome.Rejected.Malformed
        key.value == "<<" -> FrontmatterOutcome.Rejected.UnsafeConstruct
        !keys.add(key.value) -> duplicateField(key.value, topLevel)
        else -> validateNode(entry.valueNode)
      }
    }
  }

  private fun duplicateField(
    key: String,
    topLevel: Boolean,
  ): FrontmatterOutcome.Rejected =
    when {
      topLevel && key == NAME -> FrontmatterOutcome.Rejected.DuplicateName
      topLevel && key == DESCRIPTION -> FrontmatterOutcome.Rejected.DuplicateDescription
      else -> FrontmatterOutcome.Rejected.Malformed
    }

  private fun validateNode(node: Node): FrontmatterOutcome.Rejected? =
    when (node) {
      is MappingNode -> validateMapping(node, topLevel = false)
      is SequenceNode -> node.value.firstNotNullOfOrNull(::validateNode)
      else -> null
    }

  private fun readFields(root: MappingNode): FrontmatterOutcome {
    val fields = root.value.associate { (it.keyNode as ScalarNode).value to it.valueNode }
    val name = fields[NAME]
    val description = fields[DESCRIPTION]
    return when {
      name == null -> FrontmatterOutcome.Rejected.MissingName
      description == null -> FrontmatterOutcome.Rejected.MissingDescription
      name !is ScalarNode -> FrontmatterOutcome.Rejected.Malformed
      description !is ScalarNode -> FrontmatterOutcome.Rejected.Malformed
      name.value.isBlank() -> FrontmatterOutcome.Rejected.MissingName
      description.value.isBlank() -> FrontmatterOutcome.Rejected.MissingDescription
      else -> FrontmatterOutcome.Parsed(name.value, description.value)
    }
  }
}
