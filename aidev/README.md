# AI development guidance

These are portable, project-scoped versions of the skills used while building
this app. They keep the architecture decisions close to the code. `AGENTS.md`
points agents to the relevant skill; this folder does not change global agent
settings or install anything on a reviewer's machine.

## Skills already used

| Skill | Project use | Source |
| --- | --- | --- |
| [android](skills/android/SKILL.md) | Android code, manifests, resources, Gradle | Adapted from the provided Android skill |
| [kotlin](skills/kotlin/SKILL.md) | Kotlin, coroutines, cancellation | Adapted from the provided Kotlin skill |
| [compose](skills/compose/SKILL.md) | UI state, themes, strings, previews | Adapted from the provided Compose skill |
| [edge-to-edge](skills/edge-to-edge/SKILL.md) | System bars, safe areas, native dark/light startup | Project adaptation of the provided Google edge-to-edge skill |
| [navigation-3](skills/navigation-3/SKILL.md) | Typed keys, saved back stack, entry scope | Project adaptation of the provided Navigation 3 skill |
| [pdf](skills/pdf/SKILL.md) | Read and verify the challenge document | Read-only subset of the provided PDF skill |
| [skill-creator](skills/skill-creator/SKILL.md) | Maintain this guidance | Project adaptation of the provided skill-creator skill |

The adaptations contain the instructions needed here. Large platform manuals,
runtime-specific artifact tools, and unrelated migration recipes are not copied.
Navigation and startup guidance link to the official documentation instead.

## Project-specific gaps covered here

| Skill | Why it is needed |
| --- | --- |
| [project-mvi](skills/project-mvi/SKILL.md) | Preserve my combined action handling and feature-owned layers |
| [layer-mapping](skills/layer-mapping/SKILL.md) | Keep explicit data/domain and domain/UI model boundaries |
| [splash-startup](skills/splash-startup/SKILL.md) | Keep the splash-to-main handoff simple and avoid duplicate screens |
| [offline-data](skills/offline-data/SKILL.md) | Preserve hierarchy, selections, and saved content when refresh fails |
| [review-workflow](skills/review-workflow/SKILL.md) | Keep changes reviewable and record meaningful verification |

The offline skill records the implemented storage boundaries and image retention
policy. Future work still needs explicit scope and review.

Keep skills focused. Add new guidance when an actual feature or failure needs it,
and verify library APIs against their official documentation when changing them.
