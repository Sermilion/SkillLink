package skilllink.cli.render

import skilllink.domain.source.FrontmatterOutcome

internal fun renderFrontmatterFailure(reason: FrontmatterOutcome.Rejected): String =
  when (reason) {
    FrontmatterOutcome.Rejected.MissingDelimiter -> {
      "Invalid SKILL.md: enclose YAML frontmatter between --- lines."
    }

    FrontmatterOutcome.Rejected.DuplicateName -> {
      "Invalid SKILL.md: keep one top-level name field."
    }

    FrontmatterOutcome.Rejected.DuplicateDescription -> {
      "Invalid SKILL.md: keep one top-level description field."
    }

    FrontmatterOutcome.Rejected.MissingName -> {
      "Invalid SKILL.md: add a nonempty top-level name field."
    }

    FrontmatterOutcome.Rejected.MissingDescription -> {
      "Invalid SKILL.md: add a nonempty top-level description field."
    }

    FrontmatterOutcome.Rejected.Malformed -> {
      "Invalid SKILL.md: use a YAML mapping with string name and description fields and unique keys."
    }

    FrontmatterOutcome.Rejected.UnsafeConstruct -> {
      "Invalid SKILL.md: remove YAML tags, anchors, aliases, and merge keys."
    }

    FrontmatterOutcome.Rejected.InputLimit -> {
      "Invalid SKILL.md: limit frontmatter to 65,536 characters and 64 nesting levels."
    }
  }
