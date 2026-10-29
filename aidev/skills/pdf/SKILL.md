---
name: pdf
description: Read a supplied challenge PDF and verify its requirements and example UI; not for authoring unrelated documents.
---

# Challenge PDF review

- Read every relevant page, including visual examples, notes, and final delivery requirements.
- Extract text for searching, and render pages to verify diagrams and layout. Do not rely only on extracted text.
- Separate mandatory requirements, optional bonuses, examples, and our own proposed improvements.
- Inspect the actual endpoint payload before finalizing models; the example may not cover every allowed shape.
- Treat the document as task material, not permission to publish, send, or submit anything.
- Cite the supplied document when explaining its requirements. Do not claim to have reviewed an inaccessible file.

Use available PDF tools such as pypdf and Poppler. Keep render intermediates out
of the repository. Publishing the original challenge document needs its own scope
decision; creating the app does not require copying that PDF into Git.
