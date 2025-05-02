package org.gradle.test;

@Deprecated @JavaAnnotation
public class JavaClassWithAnnotation {
    @Deprecated @JavaAnnotation
    String getAnnotatedProperty() { return "hi"; }

    @Deprecated @JavaAnnotation
    void annotatedMethod() { }
}
