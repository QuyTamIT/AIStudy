---
name: ai
description: "Use this skill whenever the user needs help with ai, agents. Focus especially on requests related to: Redesign the entire existing UI of AIStudy into a high-end, modern EdTech application with its own design personality. Use installed skills: - ui-ux-pro-max - impeccable - design-system - ui-styling - frontend-ui-engineering First, check the path and read the SKILL.md that is actually on the device. REQUIREMENTS: 1. Analyze the current UI and interface framework of the Android project. 2. Create or update DESIGN.md to build a consistent Design System. 3. Premium EdTech style: clean, elegant, contemporary, human-centered; Avoid stereotypical AI-generated interfaces. 4. Design a deep color palette, beautiful typography, standard spacing, uniform icons, delicate cards and just enough micro-interactions. 5. Redesign existing screens: Login, Register, Onboarding, Home, Ask, Answer and Bottom Navigation. 6. Especially improve AnswerActivity: easy-to-read solution layout, distinguishing title, content, formula, code and interactive buttons. 7. Keep all business logic, Gemini API, Room Database and active functions intact. 8. Do not create functions in Phase 5 or later. 9. Use native Android UI that matches the existing architecture, do not arbitrarily switch frameworks. 10. Avoid overused gradients, too many cards, heavy shadows, and inconsistent layouts. 11. Check the small screen, keyboard, content scrolling, touch area and dark/light contrast. 12. Run assembleDebug after deployment. Do not automatically commit, push, merge or delete data. Before editing the code, briefly present AIStudy's entire existing UI Redesign into a modern, high-end EdTech application. Even when the user does not explicitly ask for a skill, activate it if the task clearly matches this scope."
license: MIT
metadata:
  author: my skills (FAST)
  version: "1.0.0"
compatibility: ["android"]
---

# ai

ai is a workflow execution skill for rag workflow work. It exists because multi-step work fails when inputs, checks, and stop conditions are vague. Keep each step tied to evidence, validation, and a clear completion condition.

## Workflow Goal

Use this skill for retrieval-augmented generation, knowledge grounding, embeddings, and context assembly.

Use this skill for rag workflow tasks that match the user-provided scope and project evidence.

User-provided scope: Redesign the entire existing UI of AIStudy into a high-end, modern EdTech application with its own design personality. Use installed skills: - ui-ux-pro-max - impeccable - design-system - ui-styling - frontend-ui-engineering First, check the path and read the SKILL.md that is actually on the device. REQUIREMENTS: 1. Analyze the current UI and interface framework of the Android project. 2. Create or update DESIGN.md to build a consistent Design System. 3. Premium EdTech style: clean, elegant, contemporary, human-centered; Avoid stereotypical AI-generated interfaces. 4. Design a deep color palette, beautiful typography, standard spacing, uniform icons, delicate cards and just enough micro-interactions. 5. Redesign existing screens: Login, Register, Onboarding, Home, Ask, Answer and Bottom Navigation. 6. Especially improve AnswerActivity: easy-to-read solution layout, distinguishing title, content, formula, code and interactive buttons. 7. Keep all business logic, Gemini API, Room Database and active functions intact. 8. Do not create functions in Phase 5 or later. 9. Use native Android UI that matches the existing architecture, do not arbitrarily switch frameworks. 10. Avoid overused gradients, too many cards, heavy shadows, and inconsistent layouts. 11. Check the small screen, keyboard, content scrolling, touch area and dark/light contrast. 12. Run assembleDebug after deployment. Do not automatically commit, push, merge or delete data. Before editing the code, briefly present AIStudy's entire existing UI Redesign into a modern, high-end EdTech application.

## Workflow Lens

Keep the workflow explainable by preserving inputs, decisions, and final evidence. Tune decisions around these scope signals: redesign, entire, existing, aistudy, into.

Focus anchors:

- Connect entire decisions to existing constraints.
- Decision bias: Make tool choice depend on the workflow boundary and available evidence.
- Verification bias: Confirm the final output includes enough evidence to reproduce the path.
- Output bias: Prefer short structured sections only when they make the result easier to act on.

Priority signals:

- decision records
- source evidence
- clear completion criteria
- Rely on local project evidence and this skill scope when no external reference is available.

Avoid:

- opaque summaries
- lost context
- unattributed claims

## When To Use

- The task is a multi-step process with inputs, transformations, tools, and validation.
- The user needs a repeatable workflow rather than a one-off answer.
- The work benefits from checkpoints, fallback behavior, and clear stop conditions.

## When Not To Use

- The task is a single isolated code edit with no pipeline or external dependency.
- The user needs a static reference guide rather than an executable workflow.

## Pipeline

1. Identify source documents, freshness needs, chunking strategy, retrieval query, and answer constraints.
2. Separate ingestion, indexing, retrieval, reranking, prompt assembly, and answer validation concerns.
3. Preserve citations or source references when the product requires traceability.
4. Test with realistic queries that include missing, ambiguous, and conflicting information.

## Decision Points

- Identify inputs, outputs, tools, and stop conditions before executing the workflow.
- Validate intermediate results before moving to the next step.
- Escalate or ask for missing external state when the workflow cannot proceed safely.

## Failure Handling

- Handle missing context explicitly.
- Keep retries bounded and observable.
- Return partial findings with clear caveats when complete execution is not possible.

## AI Workflow Matrix

| Stage | Decision | Evidence |
| --- | --- | --- |
| Input | What context is trusted? | User prompt, files, docs, metadata. |
| Retrieval | What sources are allowed? | Indexed docs, local files, approved APIs. |
| Reasoning | What constraints guide the answer? | Schema, examples, policies, tests. |
| Output | What format is required? | Markdown, JSON, patch, report, command. |
| Evaluation | How is quality checked? | Tests, citations, assertions, examples. |

## Structured Output Shape

```json
{
  "result": "short outcome",
  "evidence": ["source or file checked"],
  "next_action": "concrete follow-up or null"
}
```

## Quick Reference

- Corpus.
- Chunking.
- Embedding.
- Retrieval.
- Answer grounding.
- Evaluation.

## Output Format

Return a RAG plan or implementation with data flow, retrieval choices, and evaluation checks.

Include the concrete outcome, key files or commands, and caveats that affect correctness.

## Example

Input: Design RAG for internal docs search.
Output: A staged RAG pipeline with indexing, retrieval, prompt assembly, and eval cases.

## Common Mistakes

- Do not treat vector similarity as proof of correctness.
- Do not omit source traceability when the answer depends on documents.

## Reference Targets

Inspect these references when they exist in the project:

- `document sources`
- `embedding provider`
- `vector store`
