package org.gradle.api.plugins.sonar

import java.lang.annotation.*

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface ProjectProperty {
    String value()
}