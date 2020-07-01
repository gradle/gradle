package org.gradle.test;

/**
 * This is a java class.
 */
public class JavaClass extends A implements CombinedInterface, JavaInterface {
    /**
     * A read-only property.
     */
    public String getReadOnly() {
        return "value";
    }

    /**
     * An ignored field.
     */
    String ignoreMe1;

    /**
     * Another ignored field.
     */
    final long ignoreMe2 = 9;

    /**
     * Not a setter.
     */
    public void setIgnoreMe1() {
    }

    /**
     * Not a setter.
     */
    public void setIgnoreMe2(String a, int b) {
    }

    /**
     * A write-only property.
     */
    public void setWriteOnly(JavaInterface value) {
    }

    /**
     * A property.
     */
    public JavaInterface getSomeProp() {
        return this;
    }

    /**
     * The setter for a property.
     * @param value
     */
    public void setSomeProp(JavaInterface value) {
    }

    /**
     * A boolean property.
     */
    boolean isFlag() {
        return false;
    }

    /**
     * An array property.
     */
    public JavaInterface[][][] getArrayProp() {
        return null;
    }
}
