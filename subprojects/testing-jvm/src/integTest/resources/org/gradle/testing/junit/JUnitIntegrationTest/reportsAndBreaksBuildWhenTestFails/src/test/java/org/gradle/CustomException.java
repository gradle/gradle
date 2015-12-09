package org.gradle;

import org.junit.Test;

public class CustomException {
    @Test
    public void custom() {
        throw new CustomException.CustomRuntimeException();
    }

    private static class CustomRuntimeException extends RuntimeException {
        @Override
        public String toString() {
            return "Exception with a custom toString implementation";
        }
    }
}