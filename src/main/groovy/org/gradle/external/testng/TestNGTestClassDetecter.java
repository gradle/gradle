package org.gradle.external.testng;

import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.gradle.api.testing.execution.TestClassVisitor;
import org.gradle.api.testing.TestFrameworkDetector;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
class TestNGTestClassDetecter extends TestClassVisitor {

    private static final Logger LOG = LoggerFactory.getLogger(TestNGTestClassDetecter.class);

    private boolean isAbstract = false;
    private String className = null;
    private String superClassName = null;
    private boolean test = false;

    TestNGTestClassDetecter(final TestFrameworkDetector detector) {
        super(detector);
    }

    /**
     * Visits the header of the class.
     *
     * @param version    the class version.
     * @param access     the class's access flags (see {@link org.objectweb.asm.Opcodes}). This
     *                   parameter also indicates if the class is deprecated.
     * @param name       the internal name of the class (see
     *                   {@link org.objectweb.asm.Type#getInternalName() getInternalName}).
     * @param signature  the signature of this class. May be <tt>null</tt> if
     *                   the class is not a generic one, and does not extend or implement
     *                   generic classes or interfaces.
     * @param superName  the internal of name of the super class (see
     *                   {@link org.objectweb.asm.Type#getInternalName() getInternalName}). For interfaces,
     *                   the super class is {@link Object}. May be <tt>null</tt>, but
     *                   only for the {@link Object} class.
     * @param interfaces the internal names of the class's interfaces (see
     *                   {@link org.objectweb.asm.Type#getInternalName() getInternalName}). May be
     *                   <tt>null</tt>.
     */
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;

        this.className = name;
        this.superClassName = superName;
    }



    /**
     * Visits an annotation of the class.
     *
     * @param desc    the class descriptor of the annotation class.
     * @param visible <tt>true</tt> if the annotation is visible at runtime.
     * @return a visitor to visit the annotation values, or <tt>null</tt> if
     *         this visitor is not interested in visiting this annotation.
     */
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return new EmptyVisitor();
    }

    /**
     * Visits information about an inner class. This inner class is not
     * necessarily a member of the class being visited.
     *
     * @param name      the internal name of an inner class (see
     *                  {@link org.objectweb.asm.Type#getInternalName() getInternalName}).
     * @param outerName the internal name of the class to which the inner class
     *                  belongs (see {@link org.objectweb.asm.Type#getInternalName() getInternalName}). May
     *                  be <tt>null</tt> for not member classes.
     * @param innerName the (simple) name of the inner class inside its
     *                  enclosing class. May be <tt>null</tt> for anonymous inner
     *                  classes.
     * @param access    the access flags of the inner class as originally declared
     *                  in the enclosing class.
     */
    public void visitInnerClass(
            String name,
            String outerName,
            String innerName,
            int access) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        boolean isPublic = (access & Opcodes.ACC_PUBLIC) != 0;
        if( outerName != null && innerName != null && isStatic && isPublic ) {
            final File innerTestClassFile = new File(detector.getTestClassesDirectory(), className + "$" + innerName + ".class");
            if ( innerTestClassFile.exists() ) {
                if ( detector.processPossibleTestClass(innerTestClassFile) )
                    LOG.debug("test-class-scan : [inner test class] : "+className+" : [name: " + name + ", outerName: " + outerName + ", innerName: " + innerName + "]");
            }
        }
    }



    /**
     * Visits a method of the class. This method <i>must</i> return a new
     * {@link org.objectweb.asm.MethodVisitor} instance (or <tt>null</tt>) each time it is
     * called, i.e., it should not return a previously returned visitor.
     *
     * @param access     the method's access flags (see {@link org.objectweb.asm.Opcodes}). This
     *                   parameter also indicates if the method is synthetic and/or
     *                   deprecated.
     * @param name       the method's name.
     * @param desc       the method's descriptor (see {@link org.objectweb.asm.Type Type}).
     * @param signature  the method's signature. May be <tt>null</tt> if the
     *                   method parameters, return type and exceptions do not use generic
     *                   types.
     * @param exceptions the internal names of the method's exception classes
     *                   (see {@link org.objectweb.asm.Type#getInternalName() getInternalName}). May be
     *                   <tt>null</tt>.
     * @return an object to visit the byte code of the method, or <tt>null</tt>
     *         if this class visitor is not interested in visiting the code of
     *         this method.
     */
    public MethodVisitor visitMethod(
            int access,
            String name,
            String desc,
            String signature,
            String[] exceptions) {
        if ( !isAbstract && !test )
            return new TestNGTestMethodDetecter(this);
        else
            return new EmptyVisitor();
    }

    public String getClassName() {
        return className;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public boolean isTest() {
        return test;
    }

    void setTest(boolean test) {
        this.test = test;
    }

    public String getSuperClassName() {
        return superClassName;
    }

}
