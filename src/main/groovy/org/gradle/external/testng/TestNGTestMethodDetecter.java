package org.gradle.external.testng;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.commons.EmptyVisitor;

/**
 * @author Tom Eyckmans
 */
class TestNGTestMethodDetecter extends EmptyVisitor {
    private final TestNGTestClassDetecter testClassDetecter;

    public TestNGTestMethodDetecter(TestNGTestClassDetecter testClassDetecter) {
        this.testClassDetecter = testClassDetecter;
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if ("Lorg/testng/annotations/Test;".equals(desc))
            testClassDetecter.setTest(true);
        return new EmptyVisitor();
    }

    public AnnotationVisitor visitAnnotationDefault() {
        return new EmptyVisitor();
    }

    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      return new EmptyVisitor();
    }
}
