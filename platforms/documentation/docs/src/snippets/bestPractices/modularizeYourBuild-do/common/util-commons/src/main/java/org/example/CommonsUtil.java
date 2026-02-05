package org.example;

import org.apache.commons.lang.StringUtils;

public class CommonsUtil implements Util {
    @Override
    public String greet() {
        return "Hello from " + StringUtils.capitalize("commons-util");
    }
}
