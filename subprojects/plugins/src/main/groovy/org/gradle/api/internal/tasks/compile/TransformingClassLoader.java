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

import com.google.common.io.ByteStreams;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.ClassPath;
import org.gradle.util.MutableURLClassLoader;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.EmptyVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms @GroovyASTTransformationClass(classes = {classLiterals}) into @GroovyASTTransformationClass([classNames]), to work around Groovy's
 * loading of transformer classes.
 */
class TransformingClassLoader extends MutableURLClassLoader {
    private static final String ANNOTATION_DESCRIPTOR = Type.getType(GroovyASTTransformationClass.class).getDescriptor();

    public TransformingClassLoader(ClassPath classpath) {
        super(null, classpath);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL resource = findResource(name.replace(".", "/") + ".class");
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        try {
            byte[] bytes = loadBytecode(resource);
            bytes = transform(bytes);
            return super.defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] loadBytecode(URL resource) throws IOException {
        InputStream inputStream = resource.openStream();
        try {
            return ByteStreams.toByteArray(inputStream);
        } finally {
            inputStream.close();
        }
    }

    private byte[] transform(byte[] bytes) {
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

    private static class AnnotationDetector implements ClassVisitor {
        private boolean found;

        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        }

        public void visitSource(String source, String debug) {
        }

        public void visitOuterClass(String owner, String name, String desc) {
        }

        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(ANNOTATION_DESCRIPTOR)) {
                found = true;
            }
            return null;
        }

        public void visitAttribute(Attribute attr) {
        }

        public void visitInnerClass(String name, String outerName, String innerName, int access) {
        }

        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            return null;
        }

        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            return null;
        }

        public void visitEnd() {
        }
    }

    private static class TransformingAdapter extends ClassAdapter {
        public TransformingAdapter(ClassWriter classWriter) {
            super(classWriter);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(ANNOTATION_DESCRIPTOR)) {
                return new AnnotationTransformingVisitor(super.visitAnnotation(desc, visible));
            }
            return super.visitAnnotation(desc, visible);
        }

        private static class AnnotationTransformingVisitor implements AnnotationVisitor {
            private final AnnotationVisitor annotationVisitor;
            private final List<String> names = new ArrayList<String>();

            public AnnotationTransformingVisitor(AnnotationVisitor annotationVisitor) {
                this.annotationVisitor = annotationVisitor;
            }

            public void visit(String name, Object value) {
                annotationVisitor.visit(name, value);
            }

            public void visitEnum(String name, String desc, String value) {
                annotationVisitor.visitEnum(name, desc, value);
            }

            public AnnotationVisitor visitAnnotation(String name, String desc) {
                return annotationVisitor.visitAnnotation(name, desc);
            }

            public AnnotationVisitor visitArray(String name) {
                if (name.equals("classes")) {
                    return new EmptyVisitor(){
                        @Override
                        public void visit(String name, Object value) {
                            Type type = (Type) value;
                            names.add(type.getClassName());
                        }
                    };
                } else if (name.equals("value")) {
                    return new EmptyVisitor() {
                        @Override
                        public void visit(String name, Object value) {
                            String type = (String) value;
                            names.add(type);
                        }
                    };
                } else {
                    return annotationVisitor.visitArray(name);
                }
            }

            public void visitEnd() {
                if (!names.isEmpty()) {
                    AnnotationVisitor visitor = annotationVisitor.visitArray("value");
                    for (String name : names) {
                        visitor.visit(null, name);
                    }
                    visitor.visitEnd();
                }
                annotationVisitor.visitEnd();
            }
        }
    }
}
