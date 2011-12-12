package org.gradle.integtests.fixtures;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TargetGradleVersions {
    String[] value();
}
