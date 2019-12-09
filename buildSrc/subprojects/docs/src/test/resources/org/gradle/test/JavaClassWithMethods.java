package org.gradle.test;

public class JavaClassWithMethods {
    public JavaClassWithMethods(String value) {
    }

    /**
     * A method that returns String.
     */
    String stringMethod(String stringParam) {
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
    CombinedInterface refTypeMethod(JavaInterface refParam, boolean aFlag) {
        return null;
    }

    /**
     * A method that returns an array
     */
    String[][] arrayMethod(String[]... strings) {
        return null;
    }

    int getIntProp() {
        return 5;
    }

    void setIntProp(int prop) {
    }
}
