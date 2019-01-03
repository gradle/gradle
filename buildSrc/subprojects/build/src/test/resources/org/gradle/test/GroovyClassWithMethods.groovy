package org.gradle.test

class GroovyClassWithMethods {

    GroovyClassWithMethods(String prop) {
        this.prop = prop
    }

    /**
     * A method that returns String.
     */
    String stringMethod(String stringParam) {
        'value'
    }

    /**
     * A method that returns void.
     */
    void voidMethod() {
    }

    /**
     * A method that returns a reference type.
     */
    public final CombinedInterface refTypeMethod(JavaInterface someThing, boolean aFlag) {
        null
    }

    /**
     * A method that returns a default type.
     */
    def defMethod(def defParam) {
        null
    }

    /**
     * A method that returns an array
     */
    String[][] arrayMethod(String[]... strings) {
        null
    }

    /**
     * A String property.
     */
    String prop

    /**
     * A read-only property.
     */
    final JavaInterface finalProp

    int getIntProp() { 5 }

    void setIntProp(int prop) { }
}

