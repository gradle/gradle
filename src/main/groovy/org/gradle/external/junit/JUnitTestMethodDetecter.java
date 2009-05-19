package org.gradle.external.junit;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.commons.EmptyVisitor;

/**
 * @author Tom Eyckmans
 */
class JUnitTestMethodDetecter extends EmptyVisitor {

    private final JUnitTestClassDetecter testClassDetecter;

    JUnitTestMethodDetecter(JUnitTestClassDetecter testClassDetecter) {
        this.testClassDetecter = testClassDetecter;
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if ("Lorg/junit/Test;".equals(desc))
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
