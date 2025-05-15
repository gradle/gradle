package org.example;

import com.google.common.base.CaseFormat;

public class GuavaUtil implements Util {
    @Override
    public String greet() {
        return "Hello from " + CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, "guava-util");
    }
}
