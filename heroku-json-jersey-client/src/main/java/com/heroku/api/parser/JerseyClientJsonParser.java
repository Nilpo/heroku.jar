package com.heroku.api.parser;

import com.heroku.api.exception.ParseException;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.JavaType;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author Ryan Brainard
 */
public class JerseyClientJsonParser implements Parser {

    @Override
    public String encode(Object object) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibilityChecker(mapper.getSerializationConfig().getDefaultVisibilityChecker()
            .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
            .withGetterVisibility(JsonAutoDetect.Visibility.ANY)
            .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
            .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        try {
            return mapper.writeValueAsString(object);
        } catch (IOException e) {
            throw new ParseException("Unable to encode object.", e);
        }
    }

    @Override
    public <T> T parse(byte[] data, Type type) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JavaType javaType = mapper.constructType(type);
        try {
            return mapper.readValue(data, javaType);
        } catch (IOException e) {
            throw new ParseException("Unable to parse data.", e);
        }
    }
}
