package org.gradle.sample;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;

public class Greeter {
    public String getGreeting() {
        LogManager.getRootLogger().info("generating greeting.");
        return "hello " + StringUtils.capitalize("gradle");
    }
}