package org.gradle.sample;

import org.apache.log4j.LogManager;

public class SimpleGreeter {
    public String getGreeting() throws Exception {
        LogManager.getRootLogger().info("generating greeting.");
        return "Hello world!";
    }
}
