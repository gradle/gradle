package org.gradle;

import org.junit.Test;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class UnserializableException {

    @Test
    public void unserialized() {
        throw new UnserializableRuntimeException("whatever", null);
    }

    static class UnserializableRuntimeException extends RuntimeException {
        UnserializableRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }

        private void writeObject(ObjectOutputStream outstr) throws IOException {
            outstr.writeObject(new Object());
        }
    }
}
