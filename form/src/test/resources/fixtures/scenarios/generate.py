"""Regenerate deterministic local scenarios; no network or third-party packages."""

import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
cases = []


def text(node_id, content):
    return dict(type="text", id=node_id, content=content)


def section(node_id, title, items):
    return dict(type="section", id=node_id, title=title, items=items)


def page(items, node_id=1, title="Local form"):
    return [dict(type="page", id=node_id, title=title, items=items)]


def choice(node_id, set_id, labels, multiple=False):
    return dict(type="choice", id=node_id, content=f"Question {node_id}", response_set=dict(
        id=set_id, multiple_selection=multiple,
        responses=[dict(id=i + 1, label=label) for i, label in enumerate(labels)]))


def image(node_id, name, title):
    return dict(type="image", id=node_id, title=title, src=f"https://fixtures.example.invalid/{name}")


def flatten(nodes):
    for node in nodes:
        yield node
        yield from flatten(node.get("items", []))


def write(name, payload, note, accept=True, ui=False, compact=False):
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=None if compact else 2) + "\n", encoding="utf-8")
    case = dict(file=name, accept=accept, note=note, ui=ui)
    if accept:
        nodes = list(flatten(payload))
        case.update(rows=len(nodes), pages=len(payload), lastId=nodes[-1]["id"] if nodes else None)
    cases.append(case)


write("valid/empty-document.json", [], "A successfully fetched empty form is not a loading state.", ui=True)
write("valid/empty-containers.json", page([section(2, "Empty section", [])]) + page([], 3, "Empty page"),
      "Empty pages and sections remain visible.", ui=True)

single = choice(5, 100, ["Safe", "Needs attention", "Not applicable"])
single["response_set"]["responses"][0]["score"] = 0
single["response_set"]["responses"][1]["score"] = -2
single["response_set"]["responses"][2]["score"] = None
write("valid/mixed-hierarchy.json", page([
    text(9, "Direct page question comes before the section."),
    section(2, "Site inspection", [text(8, "Nested text"), image(4, "landscape.png", "Entrance"), single,
        section(3, "Follow-up", [choice(6, 101, ["Entrance", "Storage", "Loading bay"], True)])])
]), "All item types, direct questions, nested sections, and nullable/zero/negative scores.", ui=True)

unicode = choice(3, 200, ["Հայերեն 🇦🇲", "العربية", "עברית", "日本語", "Cafe\u0301 👨‍👩‍👧‍👦"] , True)
unicode["content"] = "Ո՞ր հատվածն է ստուգված։ / ما المناطق التي تم فحصها؟"
write("valid/unicode-and-rtl.json", page([
    text(2, "Հայերեն\nالعربية — עברית — 日本語\nCafe\u0301 • 👨‍👩‍👧‍👦 • 100%\n\"quotes\" and \\slashes"), unicode
], title="Ստուգում / فحص / בדיקה"), "Mixed scripts, bidirectional text, combining marks, emoji, escapes, and line breaks.", ui=True)

long = choice(3, 300, ["A long option label that should stay readable by horizontal scrolling. " * 4,
    "UNBROKEN" * 64, "Short", "Line one\nLine two\nLine three"])
long["content"] = "A question with a longer description. " * 12
write("valid/long-copy.json", page([text(2, "Long paragraph. " * 100), long,
    image(4, "portrait.png", "A long image title. " * 18)], title="A long page title. " * 12),
    "Long titles, paragraphs, unbroken and multiline option labels.", ui=True)

shared = choice(2, 400, ["Shared A", "Shared B"], True)
same = copy.deepcopy(shared)
same.update(id=3, content="Same definitions, independent answers")
write("valid/shared-response-sets.json", page([shared, same, choice(4, 401, ["Other A", "Other B"])]),
      "Two questions share a set; another set reuses option IDs without sharing answers.", ui=True)

large_ids = page([
    text(9223372036854775807, "Long.MAX_VALUE"), section(0, "Zero ID", [
        text(-9223372036854775808, "Long.MIN_VALUE"), choice(9000000000, -1, ["First", "Second"])])
], node_id=-5)
large_ids[0]["items"][1]["items"][1]["response_set"]["responses"][0]["score"] = 2147483647
large_ids[0]["items"][1]["items"][1]["response_set"]["responses"][1]["score"] = -2147483648
write("valid/large-and-unordered-ids.json", large_ids,
      "Signed Long/Int boundaries, zero, nonsequential IDs; source order must survive storage.", ui=True)

leaf = text(1000, "Deepest leaf")
for i in reversed(range(2, 42)):
    leaf = section(i, f"Nested section {i - 1}", [leaf])
write("valid/deep-sections.json", page([leaf]), "40 nested sections; visual indentation should remain capped.", ui=True, compact=True)

write("valid/many-options.json", page([
    choice(2, 500, [f"Option {i:02d}" for i in range(1, 65)]),
    choice(3, 501, [f"Area {i:02d}" for i in range(1, 65)], True)
]), "64 chips per question; the final option must remain reachable.", ui=True)

write("valid/many-rows.json", page([
    section(10 + group, f"Section {group + 1}", [text(1000 + group * 25 + i, f"Row {group * 25 + i + 1}")
        for i in range(25)]) for group in range(10)
]), "250 text rows; scroll to the last row and retain stable identities.", ui=True, compact=True)

write("valid/image-variants.json", page([
    image(2, "landscape.png", "Landscape"), image(3, "portrait.png", "Portrait"),
    image(4, "transparent.png", "Transparent artwork"), image(5, "landscape.png", "Same URL, different ID"),
    image(6, "missing.png", "404 image"), image(7, "not-image.png", "HTTP 200 with invalid bytes")
]), "Tests serve landscape, portrait, transparency, repeated URLs, 404, and corrupt bytes locally.", ui=True)

metadata = page([text(2, "Metadata should not create or remove rows.")])
metadata[0].update(version=99, theme=dict(background="#ff0000"), extra=dict(nested=["type", "video"]))
metadata[0]["items"][0]["future_metadata"] = dict(required=False)
write("valid/extra-metadata.json", metadata, "Unknown metadata is ignored; JSON theme metadata does not style the UI.", ui=True)

write("probe/blank-display-values.json", page([text(2, ""), choice(3, 600, ["", "   ", "\n"]),
    choice(4, 601, [])], title=""), "Accepted by the current schema; inspect unnamed chips and questions without options.", ui=True)

before = page([choice(2, 700, ["A", "B", "C"], True), choice(3, 701, ["X", "Y"]), text(4, "Before")])
after = page([text(4, "After"), choice(3, 701, ["X", "Y"]), choice(2, 700, ["C", "B"])])
after[0]["items"][-1]["response_set"]["responses"] = [dict(id=3, label="C updated"), dict(id=2, label="B")]
replacement = page([choice(2, 702, ["New A", "New B"])])
write("refresh/01-before.json", before, "Select options 1 and 3 on question 2 and option 2 on question 3.")
write("refresh/02-reordered.json", after, "Remove option 1, reorder content, rename C, and change multiple to single; keep option 3.")
write("refresh/03-replaced-set.json", replacement, "Replacing the response set and removing another question drops stale answers.")

write("invalid/unsupported-video.json", page([text(2, "Do not save this partial replacement."),
    dict(type="video", id=3, src="https://fixtures.example.invalid/clip.mp4"), text(4, "After unsupported item")]),
    "Reject the complete snapshot, including supported siblings.", accept=False)
write("invalid/unsupported-nested.json", page([section(2, "Nested unsupported", [
    dict(type="signature", id=3, content="Sign here")])]), "Unsupported type nested inside a section.", accept=False)
write("invalid/unsupported-root.json", [dict(type="form", id=1, items=[])], "Unsupported root discriminator.", accept=False)
write("invalid/duplicate-content-ids.json", page([text(2, "First")]) + page([text(2, "Duplicate across pages")], 3),
      "Global node IDs must be unique.", accept=False)
duplicate = choice(2, 800, ["A", "B"])
duplicate["response_set"]["responses"][1]["id"] = 1
write("invalid/duplicate-option-ids.json", page([duplicate]), "Options within a response set must have unique IDs.", accept=False)
write("invalid/conflicting-response-sets.json", page([choice(2, 800, ["A", "B"]), choice(3, 800, ["A", "Different B"])]),
      "Same response-set ID with conflicting definitions.", accept=False)
write("invalid/nested-page.json", page([section(2, "Section", page([], 3))]), "Pages belong at the root.", accept=False)
write("invalid/non-page-root.json", [text(1, "Root text")], "A supported item type in an unsupported hierarchy position.", accept=False)
write("invalid/missing-type.json", page([dict(id=2, content="Missing discriminator")]), "Missing type discriminator.", accept=False)
write("invalid/missing-choice-mode.json", page([dict(type="choice", id=2, content="Choose",
    response_set=dict(id=800, responses=[]))]), "Selection mode must not be defaulted.", accept=False)
write("invalid/null-required-field.json", page([dict(type="text", id=2, content=None)]), "Required text cannot be null.", accept=False)
write("invalid/id-overflow.json", page([text(9223372036854775808, "Too large for Long")]), "Out-of-range numeric ID.", accept=False)
overflow = choice(2, 800, ["Too large for Int"])
overflow["response_set"]["responses"][0]["score"] = 2147483648
write("invalid/score-overflow.json", page([overflow]), "Out-of-range score.", accept=False)
write("invalid/wrong-items-shape.json", [dict(type="page", id=1, title="Page", items={})], "Items must be an array.", accept=False)
leaf = json.dumps(text(10000, "Deep payload must fail safely"), ensure_ascii=False)
# Build this one as text so older Python runtimes do not hit their own nesting limit.
for i in reversed(range(2, 514)):
    header = json.dumps(dict(type="section", id=i, title="Nested"))
    leaf = header[:-1] + ', "items": [' + leaf + ']}'
header = json.dumps(dict(type="page", id=1, title="Local form"))
(ROOT / "invalid/excessive-nesting.json").write_text(
    '[' + header[:-1] + ', "items": [' + leaf + ']}]\n', encoding="utf-8")
cases.append(dict(file="invalid/excessive-nesting.json", accept=False,
    note="Excessive nesting must become a typed content failure, never a stack overflow.", ui=False))

path = ROOT / "invalid/truncated.json"
path.write_text('[{"type":"page","id":1,"title":"Interrupted download","items":[\n', encoding="utf-8")
cases.append(dict(file="invalid/truncated.json", accept=False, ui=False, note="Deliberately malformed, truncated JSON."))
(ROOT / "index.json").write_text(json.dumps(dict(cases=cases), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {len(cases)} scenarios to {ROOT}")
