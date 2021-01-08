package org.gradle;

import org.junit.Test;

public class BrokenException {
    @Test
    public void broken() {
        // Exception's toString() throws an exception
        throw new BrokenRuntimeException();
    }

    private static class BrokenRuntimeException extends RuntimeException {
        @Override
        public String toString() {
            throw new UnsupportedOperationException();
        }
    }
}