package org.gradle.sample;

import org.apache.commons.lang.StringUtils;

public class Greeter {
    public String getGreeting() {
        return "hello " + StringUtils.capitalize("gradle");
    }
}