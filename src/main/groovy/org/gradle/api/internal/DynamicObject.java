package org.gradle.api.internal;

import org.gradle.api.plugins.Convention;

import java.util.Map;

import groovy.lang.MissingPropertyException;
import groovy.lang.MissingMethodException;

public interface DynamicObject {
    boolean hasProperty(String name);

    Object property(String name) throws MissingPropertyException;

    void setProperty(String name, Object value) throws MissingPropertyException;

    Map<String, Object> properties();

    boolean hasMethod(String name, Object... params);

    Object invokeMethod(String name, Object... params) throws MissingMethodException;
}
