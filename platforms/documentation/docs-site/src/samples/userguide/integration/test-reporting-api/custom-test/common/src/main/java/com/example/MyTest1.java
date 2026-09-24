package com.example;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MyTest1 {

    @Test
    void testNullPointer() {
        List<Object> list = List.of(1, null, 3);

        for (Object item : list) {
            Integer number = (Integer) item; // Will throw NullPointerException for null item
            assertTrue(number instanceof Integer, "Item should be of type Integer");
        }
    }

}