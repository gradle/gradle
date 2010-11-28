package org.gradle.test

/**
 * This is a groovy class.
 */
class GroovyClass extends A implements GroovyInterface, JavaInterface {
    /**
     * A groovy property.
     */
    GroovyInterface groovyProp

    /**
     * A read-only groovy property.
     */
    final String readOnlyGroovyProp

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
    GroovyInterface getSomeProp() {
        this
    }

    void setSomeProp(GroovyInterface value) {
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
