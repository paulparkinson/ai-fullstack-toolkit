package com.oracle.ai.fullstack.simulator;

import com.oracle.ai.fullstack.execution.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Deliberate fixtures, not database evidence. Graph traverses edges; no SQL or join emulator. */
public final class SimulatorData {
    public record Item(String sku, String product, double risk, String source, String destination,
                       List<Double> sourceCoordinates, List<Double> destinationCoordinates) {}
    private final List<Item> items = List.of(
        new Item("SKU-APAC-210", "Cooling controller", .91, "Sydney", "Singapore", List.of(151.2093, -33.8688), List.of(103.8198, 1.3521)),
        new Item("SKU-500", "Sustainable widget", .86, "Dallas", "Newark", List.of(-96.797, 32.7767), List.of(-74.1724, 40.7357)),
        new Item("SKU-700", "Low-carbon kit", .74, "Dallas", "Chicago", List.of(-96.797, 32.7767), List.of(-87.6298, 41.8781)));
    private final AtomicInteger writes = new AtomicInteger();
    public List<Item> items() { return items; }
    public int writes() { return writes.get(); }
    public Operations.Result result(Map<String,Object> data, String source) {
        return new Operations.Result(data, new Operations.Evidence(source, true, "simulation-" + UUID.randomUUID()));
    }
    public Operations.Result read(String operation, Map<String,Object> args, Operations.Context ctx) {
        if (operation.equals("list-stockout-risk")) return result(Map.of("kind", "table", "riskScale", "0–1",
            "rows", items.stream().map(i -> Map.of("sku", i.sku(), "product", i.product(), "warehouse", i.destination(), "risk", i.risk())).toList()), "simulated-managed-agent");
        String sku = (String)args.get("sku");
        Item i = items.stream().filter(item -> item.sku().equals(sku)).findFirst().orElseThrow(() -> new IllegalArgumentException("No simulated data for SKU: " + sku));
        if (operation.equals("show-spatial-hotspots")) {
            var features = List.of(point(i.source(), i.sourceCoordinates(), "source", .25), point(i.destination(), i.destinationCoordinates(), "destination", i.risk()),
                Map.of("type", "Feature", "properties", Map.of("role", "connection", "label", "Suggested transfer (not a road route)"),
                    "geometry", Map.of("type", "LineString", "coordinates", List.of(i.sourceCoordinates(), i.destinationCoordinates()))));
            return result(Map.of("kind", "spatial", "sku", sku, "geojson", Map.of("type", "FeatureCollection", "features", features)), "simulated-managed-agent");
        }
        if (operation.equals("show-supply-chain-graph")) {
            var vertices = Map.of("supplier", "Supplier / " + sku, "source", i.source(), "destination", i.destination());
            var edges = List.of(List.of("supplier", "source"), List.of("source", "destination"));
            var visited = new LinkedHashSet<String>(); var queue = new ArrayDeque<String>(); queue.add("supplier");
            while (!queue.isEmpty()) { String node = queue.remove(); if (visited.add(node)) for (var edge : edges) if (edge.get(0).equals(node)) queue.add(edge.get(1)); }
            return result(Map.of("kind", "graph", "sku", sku, "nodes", visited.stream().map(n -> Map.of("data", Map.of("id", n, "label", vertices.get(n)))).toList(),
                "edges", edges.stream().map(e -> Map.of("data", Map.of("id", e.get(0) + "-" + e.get(1), "source", e.get(0), "target", e.get(1)))).toList()), "simulated-managed-agent");
        }
        throw new IllegalArgumentException("Unknown simulated read");
    }
    private static Map<String,Object> point(String name, List<Double> coordinates, String role, double risk) {
        return Map.of("type", "Feature", "properties", Map.of("name", name, "role", role, "risk", risk), "geometry", Map.of("type", "Point", "coordinates", coordinates));
    }
    public Operations.Result write(String operation, Map<String,Object> args, Operations.Context context) {
        if (!operation.equals("approve-inventory-transfer")) throw new IllegalArgumentException("Unknown simulated write");
        return result(Map.of("status", "SIMULATED_TRANSFER_RECORDED", "transfer", args, "writeCount", writes.incrementAndGet()), "simulated-oracle-mcp-toolkit");
    }
}
