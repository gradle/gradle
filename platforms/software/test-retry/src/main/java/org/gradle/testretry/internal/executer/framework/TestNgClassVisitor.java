/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.executer.framework;

import org.gradle.testretry.internal.testsreader.TestsReader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM7;

final class TestNgClassVisitor extends TestsReader.Visitor<TestNgClassVisitor.ClassInfo> {

    private static final List<String> LIFECYCLE_ANNOTATION_DESCRIPTORS = Arrays.asList(
        "Lorg/testng/annotations/BeforeClass;",
        "Lorg/testng/annotations/BeforeTest;",
        "Lorg/testng/annotations/BeforeMethod;",
        "Lorg/testng/annotations/AfterTest;",
        "Lorg/testng/annotations/AfterClass;",
        "Lorg/testng/annotations/AfterMethod;"
    );

    private String currentMethod;

    private final ClassInfo classInfo = new ClassInfo();
    private final TestNGMethodVisitor methodVisitor = new TestNGMethodVisitor();

    final static class ClassInfo {

        private final Map<String, List<String>> dependsOn = new HashMap<>();
        private final Map<String, List<String>> dependedOn = new HashMap<>();
        private final Set<String> lifecycleMethods = new HashSet<>();

        private String superClass;

        Set<String> dependsOn(String method) {
            Set<String> dependentChain = new HashSet<>();

            List<String> search = Collections.singletonList(method);
            while (!search.isEmpty()) {
                search = search.stream()
                    .flatMap(upstream -> dependsOn.getOrDefault(upstream, Collections.emptyList()).stream())
                    .filter(upstream -> !dependentChain.contains(upstream))
                    .collect(Collectors.toList());
                dependentChain.addAll(search);
            }

            search = Collections.singletonList(method);
            while (!search.isEmpty()) {
                search = search.stream()
                    .flatMap(downstream -> dependedOn.getOrDefault(downstream, Collections.emptyList()).stream())
                    .filter(downstream -> !dependentChain.contains(downstream))
                    .collect(Collectors.toList());
                dependentChain.addAll(search);
            }

            return dependentChain;
        }

        @Nullable
        public String getSuperClass() {
            return superClass;
        }

        public Set<String> getLifecycleMethods() {
            return lifecycleMethods;
        }
    }

    @Override
    public ClassInfo getResult() {
        return classInfo;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        this.currentMethod = name;
        return methodVisitor;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classInfo.superClass = superName.replace('/', '.');
    }

    private final class TestNGMethodVisitor extends MethodVisitor {

        private final TestNGTestAnnotationVisitor testAnnotationVisitor = new TestNGTestAnnotationVisitor();

        public TestNGMethodVisitor() {
            super(ASM7);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.contains("org/testng/annotations/Test")) {
                return testAnnotationVisitor;
            } else if (LIFECYCLE_ANNOTATION_DESCRIPTORS.contains(descriptor)) {
                classInfo.lifecycleMethods.add(currentMethod);
            }
            return null;
        }
    }

    private final class TestNGTestAnnotationVisitor extends AnnotationVisitor {

        private final TestNGTestDependsOnAnnotationVisitor dependsOnAnnotationVisitor = new TestNGTestDependsOnAnnotationVisitor();

        public TestNGTestAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("dependsOnMethods".equals(name)) {
                return dependsOnAnnotationVisitor;
            }
            return null;
        }
    }

    private final class TestNGTestDependsOnAnnotationVisitor extends AnnotationVisitor {

        public TestNGTestDependsOnAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public void visit(String name, Object value) {
            classInfo.dependsOn.compute(currentMethod, (m, acc) -> {
                if (acc == null) {
                    acc = new ArrayList<>();
                }
                acc.add((String) value);
                return acc;
            });

            classInfo.dependedOn.compute((String) value, (m, acc) -> {
                if (acc == null) {
                    acc = new ArrayList<>();
                }
                acc.add(currentMethod);
                return acc;
            });
        }
    }
}
