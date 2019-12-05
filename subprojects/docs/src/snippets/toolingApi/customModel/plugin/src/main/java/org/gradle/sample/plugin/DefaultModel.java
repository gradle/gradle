package org.gradle.sample.plugin;

import java.io.Serializable;
import java.lang.String;
import java.util.List;
import java.util.Arrays;

/**
 * This is the implementation of the custom tooling model. It must be serializable and must have methods and properties that
 * are compatible with the custom tooling model interface. It may or may not implement the custom tooling model interface.
 */
public class DefaultModel implements Serializable {
    public String getName() {
        return "name";
    }

    public List<String> getTasks() {
        return Arrays.asList(":a", ":b", ":c");
    }
}