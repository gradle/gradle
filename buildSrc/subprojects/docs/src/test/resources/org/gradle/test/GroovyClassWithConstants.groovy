package org.gradle.test

class GroovyClassWithConstants {
    static final int INT_CONST = 9
    public static final String STRING_CONST = 'some-string'
    static final Object OBJECT_CONST = new GroovyClassWithConstants()
    static final def BIG_DECIMAL_CONST = 1.02

    String ignored = 'ignore'
    final int ignored2 = 1001
    static def ignored3
}
