package org.gradle.test;

import org.gradle.test.sub2.Sub2Interface;

public class JavaClassWithInnerTypes implements Sub2Interface {
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

    Sub2Interface getSomeProp() {
        // ignore classes in method bodies
        class IgnoreMe {}

        // ignore anonymous classes
        return new Sub2Interface() { };
    }

    // ignore anonymous classes
    final Runnable ignoreMe = new Runnable() {
        public void run() {
        }
    };

    InnerClass.AnotherInner getInnerClassProp() { return null; }
}
