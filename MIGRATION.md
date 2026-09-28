# Standalone repository migration

On September 23, 2026, `ai-fullstack-toolkit/` moved from
`paulparkinson/oracle-ai-for-sustainable-dev` to
<https://github.com/paulparkinson/ai-fullstack-toolkit>.

The project directory is now the repository root. Its extracted Git history,
current local changes, shared development skill package, license, and application
definitions were preserved. Extraction changes commit IDs; the original history
remains in the monorepo.

Local configuration and ignored runtime/build files remain in the local checkout
and are not published. Existing deployments were not moved or restarted.
