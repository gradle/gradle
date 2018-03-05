package org.gradle.test

import org.gradle.test.sub2.GroovyInterface

class GroovyClassWithInnerTypes implements GroovyInterface {
    /**
     * This is an inner enum.
     */
    enum InnerEnum {}

    /**
     * This is an inner class.
     */
    static class InnerClass {
        InnerEnum enumProp

        /**
         * This is an inner inner class.
         */
        class AnotherInner {
            InnerClass outer
        }
    }

    GroovyInterface getSomeProp() {
        // ignore anonymous classes
        return new GroovyInterface() {}
    }

    InnerClass.AnotherInner innerClassProp
}
