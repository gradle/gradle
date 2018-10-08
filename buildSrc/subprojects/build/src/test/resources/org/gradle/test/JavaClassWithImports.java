package org.gradle.test;

import org.gradle.test.sub.*;
import org.gradle.test.sub2.GroovyInterface;

import java.io.Closeable;
import java.io.IOException;

public class JavaClassWithImports extends SubGroovyClass implements SubJavaInterface, GroovyInterface, Closeable {
    public void close() throws IOException {
    }
}
