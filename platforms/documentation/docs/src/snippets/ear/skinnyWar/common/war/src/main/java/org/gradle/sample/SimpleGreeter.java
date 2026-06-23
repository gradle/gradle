package org.gradle.sample;

import org.apache.commons.lang3.StringUtils;

public class SimpleGreeter {
    public String getGreeting() {
        return StringUtils.capitalize("hello world!");
    }
}
