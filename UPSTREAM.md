# Upstream baseline and provenance

`upstream/oracle-db-mcp-java-toolkit` is a clean, pinned source snapshot of Oracle's [Oracle Database MCP Java Toolkit](https://github.com/oracle/mcp/tree/main/src/oracle-db-mcp-java-toolkit), from upstream commit `5bb406b5b70e109a749cd16cc026422134a117b2`.

It is retained as the functional MCP reference and under its included UPL license. Generated `target/` content and local metadata are deliberately excluded. This snapshot is not built as part of this reactor and has no local modifications. The three-artifact framework layers a portable execution catalog and A2A/A2UI/MCP App adapters beside it, preserving upstream implementation history and delegating database execution over MCP.

The `tools:` YAML described in this project is application configuration for the shared registry, not a standardized MCP configuration file. MCP standardizes protocol messages; server configuration remains implementation-specific.

## Execution adapter

The shared framework now includes `OracleToolkitBackend`, which delegates allowlisted calls over MCP to an initialized client connected to the upstream toolkit. Database SQL/PLSQL execution remains owned by that server. No upstream source, license, or pinned revision was changed for this integration. The framework uses Java MCP SDK 0.17.2 for MCP App metadata support; the vendored server retains its own SDK version. Wire-contract tests use a synthetic upstream and do not claim live Oracle compatibility testing.
