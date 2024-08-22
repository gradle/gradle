/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.tools.api.impl;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

class LoggingAnnotationVisitor extends AnnotationVisitor {
    protected LoggingAnnotationVisitor(AnnotationVisitor av) {
        super(Opcodes.ASM9, av);
    }

    @Override
    public void visit(String name, Object value) {
        System.out.printf(" -> visit %s %s%n", name, value);
        super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        System.out.printf(" -> visitEnum %s %s %s%n", name, descriptor, value);
        super.visitEnum(name, descriptor, value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        System.out.printf(" -> visitAnnotation %s %s%n", name, descriptor);
        return super.visitAnnotation(name, descriptor);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        System.out.printf(" -> visitArray %s%n", name);
        return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
        System.out.printf(" -> visitEnd%n");
        super.visitEnd();
    }
}
