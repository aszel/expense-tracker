package de.example.expensetracker.booking;

import java.io.IOException;
import java.math.BigDecimal;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/** Requires a plain decimal string so JSON number parsing cannot lose precision in clients. */
public class DecimalStringDeserializer extends JsonDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING) || !parser.getText().matches("[0-9]+(\\.[0-9]+)?")) {
            return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
        }
        return new BigDecimal(parser.getText());
    }
}
