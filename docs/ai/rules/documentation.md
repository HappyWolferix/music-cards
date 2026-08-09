# Documentation rules

- `docs/architecture/` describes **current** behaviour only — no changelogs,
  no "previously we did X". Git history is the changelog.
- Behaviour change ⇒ update the matching doc in the same commit.
- New concept (new module, new payload form, new tool) ⇒ add keywords to
  `docs/ai/manifest.yml` so future agents can route to it.
- If a doc and the code disagree, the code is right — fix the doc.
- Keep AGENTS.md overlays short: local rules and traps only, no tutorials.
- Finish any docs change with `make validate-ai-docs`.
