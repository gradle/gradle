package org.example;

import com.google.common.collect.ImmutableMap;  // Comes from the Guava library

public class InitializeCollection {
    public static void main(String[] args) {
        ImmutableMap<String, Integer> immutableMap
            = ImmutableMap.of("coin", 3, "glass", 4, "pencil", 1);
    }
}
