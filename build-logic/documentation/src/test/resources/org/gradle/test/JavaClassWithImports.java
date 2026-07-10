package org.gradle.test;

import org.gradle.test.sub.*;
import org.gradle.test.sub2.Sub2Interface;

import java.io.Closeable;
import java.io.IOException;

public class JavaClassWithImports extends SubClass implements SubJavaInterface, Sub2Interface, Closeable {
    public void close() throws IOException {
    }
}
