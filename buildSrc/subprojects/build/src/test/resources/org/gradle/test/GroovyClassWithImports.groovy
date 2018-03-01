package org.gradle.test

import org.gradle.test.sub.*;

class GroovyClassWithImports extends SubGroovyClass implements SubJavaInterface, GroovyInterface {
    void close() {
    }
}

import org.gradle.test.sub2.GroovyInterface;
