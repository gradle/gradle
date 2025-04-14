package org.gradle.test;

public class JavaClassWithConstants {
    public static final String STRING_CONST = "some-string";
    protected static final Object OBJECT_CONST = new JavaClassWithConstants();
    static final char CHAR_CONST = 'a';
    static final int INT_CONST = 9;
    static String ignored = "ignore";
    String ignored2 = "ignore";
}
