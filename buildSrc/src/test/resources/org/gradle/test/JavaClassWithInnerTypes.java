package org.gradle.test;

import org.gradle.test.sub2.GroovyInterface;

public class JavaClassWithInnerTypes implements GroovyInterface {
    /**
     * This is an inner enum.
     */
    enum InnerEnum {
    }

    /**
     * This is an inner class.
     */
    static class InnerClass {
        InnerEnum getEnumProp() { return null; }

        /**
         * This is an inner inner class.
         */
        class AnotherInner {
            InnerClass getOuter() { return null; }
        }
    }

    GroovyInterface getSomeProp() {
        // ignore anonymous classes
        return new GroovyInterface() { };
    }

    InnerClass.AnotherInner getInnerClassProp() { return null; }
}
