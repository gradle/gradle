/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection;

import org.gradle.model.internal.asm.AsmConstants;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Base class for ASM test class scanners.
 */
public abstract class TestClassVisitor extends ClassVisitor {
    protected final TestFrameworkDetector detector;
    private boolean isAbstract;
    private String className;
    private String superClassName;
    private boolean test;

    protected TestClassVisitor(TestFrameworkDetector detector) {
        super(AsmConstants.ASM_LEVEL);
        if (detector == null) {
            throw new IllegalArgumentException("detector == null!");
        }
        this.detector = detector;
    }

    public String getClassName() {
        return className;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public boolean isTest() {
        return test;
    }

    protected void setTest(boolean test) {
        this.test = test;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        className = name;
        superClassName = superName;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (ignoreNonStaticInnerClass() && innerClassIsNonStatic(name, access)) {
            isAbstract = true;
        }
    }

    protected abstract boolean ignoreNonStaticInnerClass();

    private boolean innerClassIsNonStatic(String name, int access) {
        return name.equals(getClassName()) && (access & Opcodes.ACC_STATIC) == 0;
    }
}
