package org.gradle.test

class GroovyClassWithFullyQualifiedNames extends org.gradle.test.sub.SubGroovyClass implements org.gradle.test.sub.SubJavaInterface, java.lang.Runnable {
    org.gradle.test.sub.SubJavaInterface getProp() {
        this
    }

    void run() {
    }
}
