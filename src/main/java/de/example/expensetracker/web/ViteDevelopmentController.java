package de.example.expensetracker.web;

import java.net.URI;
import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** Local development only. Never forwards cookies or authentication to the asset server. */
@Profile("frontend-dev")
@RestController
public class ViteDevelopmentController {
    private final RestClient vite;

    public ViteDevelopmentController() {
        // Vite serves HTTP/1.1; avoid an h2c upgrade competing with its HMR upgrade handler.
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        vite = RestClient.builder().baseUrl("http://localhost:5173").requestFactory(factory).build();
    }

    @GetMapping(value = {"/", "/bookings/new", "/bookings/{id:[0-9]+}/edit"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() {
        var html = vite.get().uri("/__vite/").retrieve().body(String.class);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(html);
    }

    @GetMapping("/__vite/**")
    public ResponseEntity<byte[]> asset(HttpServletRequest request) {
        var uri = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        var resource = vite.get().uri(URI.create("http://localhost:5173" + uri)).retrieve().toEntity(byte[].class);
        return ResponseEntity.status(resource.getStatusCode())
                .contentType(resource.getHeaders().getContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM : resource.getHeaders().getContentType())
                .cacheControl(CacheControl.noStore()).body(resource.getBody());
    }
}
