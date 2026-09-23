# Repository guidance

The Maven reactor contains core, starter, runtime, and examples. Use `./build_all.sh` to build and test. The vendored MCP toolkit retains its own license and provenance in UPSTREAM.md.

Keep credentials, wallets, caches, and generated build files out of commits.
Run checks appropriate to the changed component. Repository relocation does not
change existing cloud resources or running deployments.

## Included skills

The complete shared skill package is in
`skills/video-blog-walkthrough/SKILL.md`, with its agent metadata and audit script.
It is discoverable through `.agents/skills/video-blog-walkthrough`.
Read this skill when creating narrated developer-blog walkthroughs.
Application tool and agent skill definitions remain with their implementations.
