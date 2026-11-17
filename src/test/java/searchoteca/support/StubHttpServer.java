package searchoteca.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor HTTP local (o do próprio JDK) para testar os clientes da ISBNdb, da Open Library e do Resend
 * sem acessar a internet. Cada caminho responde com o status e o corpo configurados.
 */
public final class StubHttpServer implements AutoCloseable {

    public record Reply(int status, String body) {}

    public record Received(String method, String pathAndQuery, String authorization, String body) {}

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    public final List<Received> received = new ArrayList<>();

    public StubHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            byte[] in;
            try (InputStream is = exchange.getRequestBody()) {
                in = is.readAllBytes();
            }
            synchronized (received) {
                received.add(new Received(exchange.getRequestMethod(), path + (query == null ? "" : "?" + query),
                        exchange.getRequestHeaders().getFirst("Authorization"), new String(in, StandardCharsets.UTF_8)));
            }
            Reply reply = replies.getOrDefault(path, new Reply(404, ""));
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
            exchange.close();
        });
        server.start();
    }

    public StubHttpServer on(String path, int status, String body) {
        replies.put(path, new Reply(status, body));
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
