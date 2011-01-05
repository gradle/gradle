/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.detection.TestClassVisitor;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

/**
 * @author Tom Eyckmans
 */
class JUnitTestClassDetecter extends TestClassVisitor {
    private boolean isAbstract;
    private String className;
    private String superClassName;
    private boolean test;

    JUnitTestClassDetecter(final TestFrameworkDetector detector) {
        super(detector);
    }

    /**
     * Visits the header of the class.
     *
     * @param version the class version.
     * @param access the class's access flags (see {@link Opcodes}). This parameter also indicates if the class is
     * deprecated.
     * @param name the internal name of the class (see {@link org.objectweb.asm.Type#getInternalName()
     * getInternalName}).
     * @param signature the signature of this class. May be <tt>null</tt> if the class is not a generic one, and does
     * not extend or implement generic classes or interfaces.
     * @param superName the internal of name of the super class (see {@link org.objectweb.asm.Type#getInternalName()
     * getInternalName}). For interfaces, the super class is {@link Object}. May be <tt>null</tt>, but only for the
     * {@link Object} class.
     * @param interfaces the internal names of the class's interfaces (see {@link org.objectweb.asm.Type#getInternalName()
     * getInternalName}). May be <tt>null</tt>.
     */
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;

        this.className = name;
        this.superClassName = superName;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (name.equals(className) && (access & Opcodes.ACC_STATIC) == 0) {
            isAbstract = true;
        }
    }

    /**
     * Visits an annotation of the class.
     *
     * @param desc the class descriptor of the annotation class.
     * @param visible <tt>true</tt> if the annotation is visible at runtime.
     * @return a visitor to visit the annotation values, or <tt>null</tt> if this visitor is not interested in visiting
     *         this annotation.
     */
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if ("Lorg/junit/runner/RunWith;".equals(desc)) {
            test = true;
        }

        return new EmptyVisitor();
    }

    /**
     * Visits a method of the class. This method <i>must</i> return a new {@link MethodVisitor} instance (or
     * <tt>null</tt>) each time it is called, i.e., it should not return a previously returned visitor.
     *
     * @param access the method's access flags (see {@link Opcodes}). This parameter also indicates if the method is
     * synthetic and/or deprecated.
     * @param name the method's name.
     * @param desc the method's descriptor (see {@link org.objectweb.asm.Type Type}).
     * @param signature the method's signature. May be <tt>null</tt> if the method parameters, return type and
     * exceptions do not use generic types.
     * @param exceptions the internal names of the method's exception classes (see {@link
     * org.objectweb.asm.Type#getInternalName() getInternalName}). May be <tt>null</tt>.
     * @return an object to visit the byte code of the method, or <tt>null</tt> if this class visitor is not interested
     *         in visiting the code of this method.
     */
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (!test) {
            return new JUnitTestMethodDetecter(this);
        } else {
            return new EmptyVisitor();
        }
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
