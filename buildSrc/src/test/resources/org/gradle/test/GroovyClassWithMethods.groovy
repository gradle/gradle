package org.gradle.test

class GroovyClassWithMethods {

    GroovyClassWithMethods(String prop) {
        this.prop = prop
    }

    /**
     * A method that returns String.
     */
    String stringMethod(String param1) {
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
    GroovyInterface refTypeMethod(JavaInterface param1, boolean param2) {
        null
    }

    /**
     * A method that returns a default type.
     */
    def defMethod(def param1) {
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

