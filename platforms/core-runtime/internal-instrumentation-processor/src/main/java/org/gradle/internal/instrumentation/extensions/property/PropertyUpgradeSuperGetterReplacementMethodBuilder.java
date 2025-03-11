/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.instrumentation.extensions.property;

import org.gradle.internal.instrumentation.api.jvmbytecode.ReplacementMethodBuilder;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

public class PropertyUpgradeSuperGetterReplacementMethodBuilder implements ReplacementMethodBuilder {

    private final String name;
    private final String superclassName;
    private final String replacementDescriptor;
    private final List<AnnotationData> annotations = new ArrayList<>();

    public PropertyUpgradeSuperGetterReplacementMethodBuilder(String name, String superclassName, String replacementDescriptor) {
        this.name = name;
        this.superclassName = superclassName;
        this.replacementDescriptor = replacementDescriptor;
    }

    @Override
    public MethodVisitor createCapturingVisitor() {
        // TODO Check if existing method only does super-call and nothing else
        return new MethodVisitor(ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                AnnotationData annotationData = new AnnotationData(descriptor, visible);
                annotations.add(annotationData);
                return new AnnotationCollector(annotationData);
            }
        };
    }

    @Override
    public void generateReplacementMethod(ClassVisitor cv) {
        // TODO Should we include a signature?
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, name, replacementDescriptor, null, null);
        mv.visitCode();

        // Load "this"
        mv.visitVarInsn(ALOAD, 0);

        // Call to super
        mv.visitMethodInsn(INVOKESPECIAL, superclassName,
            name, replacementDescriptor, false);

        // Return the result
        mv.visitInsn(ARETURN);

        // Copy annotations
        for (AnnotationData annotation : annotations) {
            AnnotationVisitor av = mv.visitAnnotation(annotation.desc, annotation.visible);
            annotation.values.forEach(av::visit);
            av.visitEnd();
        }

        // Set max stack and locals
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static class AnnotationData {
        private final String desc;
        private final boolean visible;
        private final Map<String, Object> values = new LinkedHashMap<>();

        public AnnotationData(String desc, boolean visible) {
            this.desc = desc;
            this.visible = visible;
        }
    }

    private static class AnnotationCollector extends AnnotationVisitor {
        private final AnnotationData annotationData;

        public AnnotationCollector(AnnotationData annotationData) {
            super(ASM9);
            this.annotationData = annotationData;
        }

        @Override
        public void visit(String name, Object value) {
            annotationData.values.put(name, value);
        }
    }
}
