package org.gradle.test;

public class JavaClassWithConstants {
    public static final String STRING_CONST = "some-string";
    static final char CHAR_CONST = 'a';
    static final int INT_CONST = 9;
    protected static final Object OBJECT_CONST = new JavaClassWithConstants();

    static String ignored = "ignore";
    String ignored2 = "ignore";
}
