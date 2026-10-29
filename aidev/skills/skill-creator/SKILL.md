---
name: skill-creator
description: Create or revise project skills under aidev while keeping them focused and portable.
---

# Project skill maintenance

- Put each skill in `aidev/skills/<name>/SKILL.md` with YAML `name` and `description` fields.
- Use lowercase hyphenated names. Describe the actual task that should load the skill.
- Record project-specific decisions and fragile behavior, not generic programming tutorials.
- Preserve the user's architecture, current scope, and approval requirements. Guidance is not authorization for extra work.
- Keep comments and prose short and natural. Explain decisions rather than praising the architecture.
- Add references or scripts only when they solve a concrete recurring need. Avoid empty folders and copied manuals.
- Keep links portable. Do not reference a developer's home directory or depend on a private runtime path.
- Update `aidev/README.md` and root `AGENTS.md` when routing changes.

Validate YAML frontmatter, naming, referenced paths, and the absence of unfinished
scaffolding. Run any new helper scripts and test meaningful behavior when the
skill introduces an actual executable workflow.
