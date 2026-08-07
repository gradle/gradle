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
package org.gradle.testretry.internal.filter;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.testretry.internal.testsreader.TestsReader;
import org.objectweb.asm.AnnotationVisitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AnnotationInspectorImpl implements AnnotationInspector {

    private static final Logger LOGGER = Logging.getLogger(AnnotationInspectorImpl.class);

    private final Map<String, Set<String>> cache = new HashMap<>();
    private final Map<String, Boolean> inheritedCache = new HashMap<>();

    private final TestsReader testsReader;

    public AnnotationInspectorImpl(TestsReader testsReader) {
        this.testsReader = testsReader;
    }

    @Override
    public Set<String> getClassAnnotations(String className) {
        Set<String> annotations = cache.get(className);
        if (annotations == null) {
            annotations = testsReader.readClass(className, ClassAnnotationVisitor::new)
                .orElseGet(() -> {
                    LOGGER.warn("Unable to find annotations of " + className);
                    return Collections.emptySet();
                });
            cache.put(className, annotations);
        }
        return annotations;
    }

    private boolean isInherited(String annotationClassName) {
        return inheritedCache.computeIfAbsent(annotationClassName, ignored ->
            testsReader.readClass(annotationClassName, AnnotationAnnotationVisitor::new)
                .orElseGet(() -> {
                    LOGGER.warn("Cannot determine whether @" + annotationClassName + " is inherited");
                    return false;
                })
        );
    }

    final class ClassAnnotationVisitor extends TestsReader.Visitor<Set<String>> {

        private final Set<String> found = new HashSet<>();

        @Override
        public Set<String> getResult() {
            return found.isEmpty() ? Collections.emptySet() : found;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if (!superName.equals("java/lang/Object")) {
                getClassAnnotations(superName.replace('/', '.'))
                    .stream()
                    .filter(AnnotationInspectorImpl.this::isInherited)
                    .forEach(found::add);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            found.add(classDescriptorToClassName(descriptor));
            return null;
        }

    }

    static final class AnnotationAnnotationVisitor extends TestsReader.Visitor<Boolean> {

        private boolean inherited;

        @Override
        public Boolean getResult() {
            return inherited;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals("Ljava/lang/annotation/Inherited;")) {
                inherited = true;
            }
            return null;
        }
    }

    private static String classDescriptorToClassName(String descriptor) {
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }
}
