package org.gradle.util;

import groovy.lang.Closure;
import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@ExtensionAnnotation(TestPreconditionExtension.class)
public @interface Requires {
    TestPrecondition[] value() default {TestPrecondition.NULL_REQUIREMENT};

    Class<? extends Closure<?>> adhoc() default AlwaysTrue.class;
}
