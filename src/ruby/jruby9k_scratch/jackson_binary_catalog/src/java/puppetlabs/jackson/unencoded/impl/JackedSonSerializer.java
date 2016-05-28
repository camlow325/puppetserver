package puppetlabs.jackson.unencoded.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.io.InputStream;

public class JackedSonSerializer extends StdScalarSerializer<InputStream> {
    public JackedSonSerializer() {
        super(InputStream.class);
    }


    @Override
    public void serialize(InputStream value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        // if we get here and the generator isn't our complementary one, we're
        // hosed.
        if (! (gen instanceof JackedSonGenerator)) {
            throw new IllegalStateException("JackedSonSerializer only works in combination with UnencodedInputStreamGenerator.");
        }
        gen.writeBinary(value, -1);
    }
}