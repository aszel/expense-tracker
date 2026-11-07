package de.example.expensetracker.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deliberately lists UI routes; unknown API/OIDC routes never fall through to the SPA. */
@Profile("!frontend-dev")
@RestController
public class FrontendController {
    @GetMapping(value = {"/", "/bookings/new", "/bookings/{id:[0-9]+}/edit"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() throws IOException {
        var html = new ClassPathResource("static/index.html").getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(html);
    }
}
