package skilllink.domain.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrontmatterReaderTest {
  @Test
  fun parsesMultilineDescriptionAndNestedAppFunctionsMetadata() {
    val content =
      """
      ---
      name: appfunctions
      description: Analyzes Android apps to find user workflows to expose as AppFunctions,
        such as creating a note, playing media, or sending a message.
      license: Complete terms in LICENSE.txt
      metadata:
        author: Google LLC
        keywords:
        - AppFunctions
        - Kotlin
        - KSP
        name: This nested name must not replace the skill name
        description: This nested description must not replace the skill description
      ---
      Instructions with YAML-like text: !! & * <<
      """.trimIndent()

    assertEquals(
      FrontmatterOutcome.Parsed(
        "appfunctions",
        "Analyzes Android apps to find user workflows to expose as AppFunctions, " +
          "such as creating a note, playing media, or sending a message.",
      ),
      FrontmatterReader.parse(content),
    )
  }

  @Test
  fun decodesFoldedLiteralAndQuotedDescriptionsWithoutTreatingPunctuationAsYamlSyntax() {
    val cases =
      mapOf(
        "description: >-\n  Build & test\n  Android apps" to "Build & test Android apps",
        "description: |\n  Keep this line.\n  And this line." to "Keep this line.\nAnd this line.\n",
        "description: \"Use * and & and << and !! as text\\nNext line\"" to
          "Use * and & and << and !! as text\nNext line",
        "description: 'It''s a skill'" to "It's a skill",
        "description: Does a thing # YAML comment" to "Does a thing",
      )
    for ((field, expected) in cases) {
      assertEquals(FrontmatterOutcome.Parsed("demo", expected), parseHeader("name: demo\n$field"), field)
    }
  }

  @Test
  fun acceptsFlowMetadataAndWindowsLineEndingsWithBom() {
    val content =
      "\uFEFF---\r\nname: demo\r\ndescription: text\r\n" +
        "metadata: {keywords: [Kotlin, ADB], enabled: true}\r\n---\r\nbody"

    assertEquals(FrontmatterOutcome.Parsed("demo", "text"), FrontmatterReader.parse(content))
  }

  @Test
  fun rejectsDuplicateFieldsAtTheirActualMappingLevel() {
    val cases =
      mapOf(
        "name: demo\nname: other\ndescription: text" to FrontmatterOutcome.Rejected.DuplicateName,
        "name: demo\ndescription: one\n'description': two" to FrontmatterOutcome.Rejected.DuplicateDescription,
        "name: demo\ndescription: text\nmetadata: {author: one, author: two}" to FrontmatterOutcome.Rejected.Malformed,
      )
    for ((header, expected) in cases) {
      assertEquals(expected, parseHeader(header), header)
    }
  }

  @Test
  fun preservesNumericSkillNamesAndScalarTextWithoutImplicitTypeConversion() {
    assertEquals(FrontmatterOutcome.Parsed("123", "true"), parseHeader("name: 123\ndescription: true"))
    assertEquals(FrontmatterOutcome.Parsed("demo", "null"), parseHeader("name: demo\ndescription: null"))
  }

  @Test
  fun rejectsMissingBlankAndNonScalarRequiredFields() {
    val cases =
      mapOf(
        "description: text\nmetadata: {name: nested}" to FrontmatterOutcome.Rejected.MissingName,
        "name: demo\nmetadata: {description: nested}" to FrontmatterOutcome.Rejected.MissingDescription,
        "name: ''\ndescription: text" to FrontmatterOutcome.Rejected.MissingName,
        "name: demo\ndescription: '  '" to FrontmatterOutcome.Rejected.MissingDescription,
        "name: demo\ndescription: [text]" to FrontmatterOutcome.Rejected.Malformed,
        "name: {value: demo}\ndescription: text" to FrontmatterOutcome.Rejected.Malformed,
      )
    for ((header, expected) in cases) {
      assertEquals(expected, parseHeader(header), header)
    }
  }

  @Test
  fun rejectsYamlTagsAnchorsAliasesAndMergeKeysWithoutExecutingThem() {
    val cases =
      listOf(
        "name: !!evil demo\ndescription: text",
        "name: demo\ndescription: text\nmetadata: !custom payload",
        "name: &id demo\ndescription: *id",
        "name: demo\ndescription: *undefined",
        "name: demo\ndescription: text\nmetadata: {<<: {author: demo}}",
      )
    for (header in cases) {
      assertEquals(FrontmatterOutcome.Rejected.UnsafeConstruct, parseHeader(header), header)
    }
  }

  @Test
  fun rejectsMalformedYamlAndNonMappingRoots() {
    for (header in listOf("name: [", "- demo", "name: demo\ndescription: text\n  bad: indentation")) {
      assertEquals(FrontmatterOutcome.Rejected.Malformed, parseHeader(header), header)
    }
  }

  @Test
  fun requiresCompleteDelimiterLines() {
    for (content in listOf("---bad\nname: demo\n---", "---\nname: demo\n---bad", "name: demo")) {
      assertEquals(FrontmatterOutcome.Rejected.MissingDelimiter, FrontmatterReader.parse(content), content)
    }
  }

  @Test
  fun boundsHeaderLengthAndNestingWithoutLimitingTheMarkdownBody() {
    assertEquals(
      FrontmatterOutcome.Rejected.InputLimit,
      parseHeader("name: demo\ndescription: " + "x".repeat(65_536)),
    )
    assertEquals(
      FrontmatterOutcome.Rejected.InputLimit,
      parseHeader("name: demo\ndescription: text\nmetadata: " + "[".repeat(65) + "]".repeat(65)),
    )
    assertEquals(
      FrontmatterOutcome.Parsed("demo", "text"),
      FrontmatterReader.parse("---\nname: demo\ndescription: text\n---\n" + "x".repeat(65_537)),
    )
  }

  private fun parseHeader(header: String): FrontmatterOutcome = FrontmatterReader.parse("---\n$header\n---\n")
}
