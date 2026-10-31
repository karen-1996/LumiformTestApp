---
name: layer-mapping
description: Define or change feature models and explicit mappings between data, domain, and presentation layers.
---

# Layer mapping

- Keep separate remote DTOs, Room entities, domain models, and UI models inside the feature.
- Put DTO/entity to domain mappings in `data/mapper`. Add domain-to-entity or request mappings when a write operation needs them.
- Put domain-to-UI mappings and UI models in the owning screen's `mapper` and `model` packages under `presentation/screens/<screen>`. Map use-case results before updating screen state.
- Keep domain independent of data and UI types. Repository and use-case contracts expose domain models; screen state and composables expose presentation models.
- Keep resource references, hierarchy depth, and display-specific formatting in presentation. Do not make domain models depend on Android resources.
- Keep mappers deterministic and free of network calls, database calls, coroutine launches, or state mutation. Prefer top-level extension functions in named mapper files for stateless conversions; use an injectable class only when a real injected dependency is needed.
- For UI projection, check existing rows and response definitions before constructing replacements. Answer-only updates should retain unchanged row, response-set, and option-list instances without a second ViewModel cache.
- Keep image presence and the row index in projected UI content. Routes use these values instead of scanning rows during recomposition or navigation.
- Use the stored definition version to choose full projection versus answer updates. Answer updates compare saved answers and use the index/persistent rows already in UI state; never traverse form nodes/options or rebuild the row index on that path.
- Preserve IDs, source order, nullable scores, and parent relationships. Flatten the hierarchy for the lazy list in the presentation mapper, not in composables or repository contracts.
- Map user input back to domain arguments or commands where their shapes differ. Do not create reverse mappings that have no actual caller.
- Add each mapper with the models and behavior that need it, rather than empty classes in the foundation phase.

Test meaningful transformations: nested and direct questions, sibling order,
nullable scores, selection display state, and any fields changed or omitted
between models. Mapping must not silently discard unsupported content.
