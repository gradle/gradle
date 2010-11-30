package org.gradle.test;

public class JavaClassWithMethods {
    public JavaClassWithMethods(String value) {
    }

    /**
     * A method that returns String.
     */
    String stringMethod(String param1) {
        return "value";
    }

    /**
     * A method that returns void.
     */
    void voidMethod() {
    }

    /**
     * A method that returns a reference type.
     */
    GroovyInterface refTypeMethod(JavaInterface param1, boolean param2) {
        return null;
    }

    int getIntProp() {
        return 5;
    }

    void setIntProp(int param) {
    }
}
