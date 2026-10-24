package vent.simulator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 시나리오 제어 HTTP 서버.
 *
 * <p>시연 중에 이상 상황을 바로 일으키기 위한 작은 제어 API다(#4). JDK 내장 HTTP 서버를 써서 라이브러리를 늘리지 않는다.
 * <pre>
 * POST /sites/{현장ID}/filter-clog     필터 막힘 시작
 * POST /sites/{현장ID}/filter-replace  필터 교체
 * GET  /health                         동작 확인
 * </pre>
 * 시나리오가 늘면(#5, #7) {@link #SCENARIOS}에 추가한다.
 *
 * @author predevho (AI 보조: Claude)
 * @since 2026-10-24 (#4)
 */
public final class ScenarioControlServer {

    /** 시나리오 이름 → 게이트웨이에서 실행할 동작 */
    private static final Map<String, Consumer<GatewaySimulator>> SCENARIOS = Map.of(
            "filter-clog", GatewaySimulator::clogFilter,
            "filter-replace", GatewaySimulator::replaceFilter);

    private final HttpServer server;
    private final Map<String, GatewaySimulator> gatewaysBySiteId;

    public ScenarioControlServer(int port, List<GatewaySimulator> gateways) throws IOException {
        this.gatewaysBySiteId = gateways.stream()
                .collect(Collectors.toMap(gateway -> gateway.site().siteId(), Function.identity()));
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/sites/", this::handleScenario);
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"UP\"}"));
    }

    public void start() {
        server.start();
        Log.info("시나리오 제어 API: http://localhost:%d/sites/{현장ID}/%s",
                server.getAddress().getPort(), String.join("|", SCENARIOS.keySet()));
    }

    public void stop() {
        server.stop(0);
    }

    private void handleScenario(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST만 됩니다\"}");
            return;
        }
        // /sites/{siteId}/{scenario}
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length != 4) {
            respond(exchange, 404, "{\"error\":\"경로는 /sites/{현장ID}/{시나리오}\"}");
            return;
        }
        GatewaySimulator gateway = gatewaysBySiteId.get(parts[2]);
        Consumer<GatewaySimulator> scenario = SCENARIOS.get(parts[3]);
        if (gateway == null || scenario == null) {
            respond(exchange, 404, "{\"error\":\"없는 현장 또는 시나리오\",\"scenarios\":\"%s\"}"
                    .formatted(String.join(",", SCENARIOS.keySet())));
            return;
        }
        scenario.accept(gateway);
        respond(exchange, 202, "{\"siteId\":\"%s\",\"scenario\":\"%s\"}".formatted(parts[2], parts[3]));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
