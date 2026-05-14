This is the Viaduct Open Source Software (OSS) root directory.

Viaduct is an opinionated GraphQL server.

A systems builder wanting to embed Viaduct into their web server would create an instance of [`viaduct.service.api.Viaduct`](service/api/src/main/kotlin/viaduct/service/api/Viaduct.kt) and create a route that would call the `Viaduct.execute` method.  This method, under the covers, calls [`viaduct.engine.api.Engine.execute`](engine/api/src/main/kotlin/viaduct/engine/api/Engine.kt).

For an end-to-end example of a service that embeds Viaduct, see the demonstration applications in `demoapps`, especially `demoapps/starwars`.  For more on testing with the demoapps see `demoapps/AGENTS.md`

For more information on constructing a `Viaduct` object see `service/AGENTS.md`.

For more information about the details of how Viaduct executes GraphQL operations, you can look at `engine/AGENTS.md`, although it is often helpful to start with `service/AGENTS.md` to understand how `viaduct.engine.api.Engine` instances get configured.

## Navigating the Gradle build

- [`impldocs/gradle-build-architecture.md`](impldocs/gradle-build-architecture.md) - Documents Viaduct's included-build architecture.
- [`impldocs/e2e-snapshot-test.md`](impldocs/e2e-snapshot-test.md) - Test publication process using a snapshot (good to use when you've changes the Gradle artifact logic)

## Navigating the Shared Libraries

The `shared/` directory contains libraries used across the Viaduct engine and tenant APIs:

- **`shared/codegen/`** — Bytecode generation library used to compile tenant field resolvers into JVM bytecode at startup. See `shared/codegen/AGENTS.md` for details.
- **`shared/viaductschema/`** — Unified abstraction layer for working with GraphQL schemas. See `shared/viaductschema/AGENTS.md` for details.
- **`shared/apiannotations/`** — Annotations used in the Viaduct public API.
- **`tenant/`** — The Tenant API, which application developers use to write resolvers. See `tenant/api/module.md` for package-level descriptions.

## Implementation Documentation

- [`core/shared/errors/impldocs/executor-error-boundaries.md`](core/shared/errors/impldocs/executor-error-boundaries.md) — Exception hierarchy (`PassthroughException`, `TenantException`), the two-boundary wrapping pattern on executor SPI entry points, `InvocationTargetException` unwrapping, and how attributed exceptions surface in GraphQL error responses.
- [`impldocs/modern-access-check.md`](impldocs/modern-access-check.md) — Access check architecture: `CheckerExecutorFactory` SPI, QueryPlan RSS embedding, the OER multi-slot pattern, and how checker results flow through completion.
- [`impldocs/object-lifecycles.md`](impldocs/object-lifecycles.md) — Description of the "lifecycles" of major objects over the lifetime of a Viaduct runtime instance (related to injection scopes).
- [`impldocs/subquery-execution.md`](impldocs/subquery-execution.md) — Cross-cutting documentation about the `ExecutionHandle` abstraction and how `ctx.query()`/`ctx.mutation()` drive subquery execution across the engine.
- [`impldocs/exception-hierarchy.md`](impldocs/exception-hierarchy.md) — Exception hierarchy specification: `TenantException` and `PassthroughException` marker interfaces, error handler semantics.

<!-- mulch:start -->
## Project Expertise (Mulch)
<!-- mulch-onboard-v:1 -->

This project uses [Mulch](https://github.com/jayminwest/mulch) for structured expertise management.

**At the start of every session**, run:
```bash
mulch prime
```

This injects project-specific conventions, patterns, decisions, and other learnings into your context.
Use `mulch prime --files src/foo.ts` to load only records relevant to specific files.

**Before completing your task**, review your work for insights worth preserving — conventions discovered,
patterns applied, failures encountered, or decisions made — and record them:
```bash
mulch record <domain> --type <convention|pattern|failure|decision|reference|guide> --description "..."
```

Link evidence when available: `--evidence-commit <sha>`, `--evidence-bead <id>`

Run `mulch status` to check domain health and entry counts.
Run `mulch --help` for full usage.
Mulch write commands use file locking and atomic writes — multiple agents can safely record to the same domain concurrently.

### Before You Finish

1. Discover what to record:
   ```bash
   mulch learn
   ```
2. Store insights from this work session:
   ```bash
   mulch record <domain> --type <convention|pattern|failure|decision|reference|guide> --description "..."
   ```
3. Validate and commit:
   ```bash
   mulch sync
   ```
<!-- mulch:end -->

<!-- seeds:start -->
## Issue Tracking (Seeds)
<!-- seeds-onboard-v:1 -->

This project uses [Seeds](https://github.com/jayminwest/seeds) for git-native issue tracking.

**At the start of every session**, run:
```
sd prime
```

This injects session context: rules, command reference, and workflows.

**Quick reference:**
- `sd ready` — Find unblocked work
- `sd create --title "..." --type task --priority 2` — Create issue
- `sd update <id> --status in_progress` — Claim work
- `sd close <id>` — Complete work
- `sd dep add <id> <depends-on>` — Add dependency between issues
- `sd sync` — Sync with git (run before pushing)

### Before You Finish
1. Close completed issues: `sd close <id>`
2. File issues for remaining work: `sd create --title "..."`
3. Sync and push: `sd sync && git push`
<!-- seeds:end -->

<!-- canopy:start -->
## Prompt Management (Canopy)
<!-- canopy-onboard-v:1 -->

This project uses [Canopy](https://github.com/jayminwest/canopy) for git-native prompt management.

**At the start of every session**, run:
```
cn prime
```

This injects prompt workflow context: commands, conventions, and common workflows.

**Quick reference:**
- `cn list` — List all prompts
- `cn render <name>` — View rendered prompt (resolves inheritance)
- `cn emit --all` — Render prompts to files
- `cn update <name>` — Update a prompt (creates new version)
- `cn sync` — Stage and commit .canopy/ changes

**Do not manually edit emitted files.** Use `cn update` to modify prompts, then `cn emit` to regenerate.
<!-- canopy:end -->
