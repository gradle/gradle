package org.gradle.test

/**
 * This is a groovy class.
 */
class GroovyClass extends A implements CombinedInterface, JavaInterface {
    /**
     * A groovy property.
     */
    CombinedInterface groovyProp

    /**
     * A read-only groovy property.
     */
    final String readOnlyGroovyProp

    /**
     * An array property.
     */
    def String[] arrayProp

    private def ignoreMe1;
    public int ignoreMe2;
    protected int ignoreMe3;
    static String ignoreMe4;

    /**
     * A read-only property.
     */
    def getReadOnly() {
        'value'
    }

    /**
     * A property.
     */
    CombinedInterface getSomeProp() {
        this
    }

    void setSomeProp(CombinedInterface value) {
    }

    /**
     * A write-only property.
     */
    void setWriteOnly(JavaInterface value) {
    }

    public void setIgnoreMe1() {
    }

    public void setIgnoreMe2(String a, int b) {
    }
}
