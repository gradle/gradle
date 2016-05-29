/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.gradle.internal.classloader.TransformingClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms @GroovyASTTransformationClass(classes = {classLiterals}) into @GroovyASTTransformationClass([classNames]),
 * to work around GROOVY-5416.
 */
class GroovyCompileTransformingClassLoader extends TransformingClassLoader {
    private static final String ANNOTATION_DESCRIPTOR = Type.getType(GroovyASTTransformationClass.class).getDescriptor();

    public GroovyCompileTransformingClassLoader(ClassLoader parent, ClassPath classPath) {
        super(parent, classPath);
    }

    @Override
    protected byte[] transform(String className, byte[] bytes) {
        // First scan for annotation, and short circuit transformation if not present
        ClassReader classReader = new ClassReader(bytes);

        AnnotationDetector detector = new AnnotationDetector();
        classReader.accept(detector, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE);
        if (!detector.found) {
            return bytes;
        }

        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(new TransformingAdapter(classWriter), 0);
        bytes = classWriter.toByteArray();
        return bytes;
    }

    private static class AnnotationDetector extends ClassVisitor {
        private boolean found;

        private AnnotationDetector() {
            super(Opcodes.ASM5);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(ANNOTATION_DESCRIPTOR)) {
                found = true;
            }
            return null;
        }
    }

    private static class TransformingAdapter extends ClassVisitor {
        public TransformingAdapter(ClassWriter classWriter) {
            super(Opcodes.ASM5, classWriter);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(ANNOTATION_DESCRIPTOR)) {
                return new AnnotationTransformingVisitor(super.visitAnnotation(desc, visible));
            }
            return super.visitAnnotation(desc, visible);
        }

        private static class AnnotationTransformingVisitor extends AnnotationVisitor {
            private final List<String> names = new ArrayList<String>();

            public AnnotationTransformingVisitor(AnnotationVisitor annotationVisitor) {
                super(Opcodes.ASM5, annotationVisitor);
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                if (name.equals("classes")) {
                    return new AnnotationVisitor(Opcodes.ASM5){
                        @Override
                        public void visit(String name, Object value) {
                            Type type = (Type) value;
                            names.add(type.getClassName());
                        }
                    };
                } else if (name.equals("value")) {
                    return new AnnotationVisitor(Opcodes.ASM5) {
                        @Override
                        public void visit(String name, Object value) {
                            String type = (String) value;
                            names.add(type);
                        }
                    };
                } else {
                    return super.visitArray(name);
                }
            }

            @Override
            public void visitEnd() {
                if (!names.isEmpty()) {
                    AnnotationVisitor visitor = super.visitArray("value");
                    for (String name : names) {
                        visitor.visit(null, name);
                    }
                    visitor.visitEnd();
                }
                super.visitEnd();
            }
        }
    }
}
