/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility.rules

import groovy.transform.CompileStatic
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod
import javassist.CtBehavior
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtField
import javassist.CtMethod
import javassist.Modifier
import me.champeau.gradle.japicmp.report.Violation
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.TypeReference
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

@CompileStatic
class NullabilityBreakingChangesRule extends AbstractGradleViolationRule {

    enum Nullability {
        NON_NULL, NULLABLE, UNMARKED,
    }

    private static final List<String> NULLABLE_ANNOTATIONS = [
        javax.annotation.Nullable,
        org.jetbrains.annotations.Nullable,
        org.jspecify.annotations.Nullable,
    ].collect { it.name }

    private static final String NULL_UNMARKED_ANNOTATION = org.jspecify.annotations.NullUnmarked.class.name

    NullabilityBreakingChangesRule(Map<String, Object> params) {
        super(params)
    }

    @Override
    Violation maybeViolation(JApiCompatibility member) {

        if (isNewOrRemoved(member)) {
            return null
        }

        List<String> warnings = []
        List<String> errors = []

        def inspectParametersNullabilityOf = { CtBehavior oldBehavior, CtBehavior newBehavior ->

            List<Boolean> oldParametersNullability = parametersNullabilityOf(oldBehavior)
            List<Boolean> newParametersNullability = parametersNullabilityOf(newBehavior)

            for (int idx = 0; idx < oldParametersNullability.size(); idx++) {
                def oldNullability = oldParametersNullability[idx]
                def newNullability = newParametersNullability[idx]
                if (oldNullability && !newNullability) {
                    errors << "Parameter $idx from null accepting to non-null accepting breaking change".toString()
                } else if (!oldNullability && newNullability) {
                    warnings << "Parameter $idx nullability changed from non-nullable to nullable".toString()
                }
            }
        }

        if (member instanceof JApiClass) {

            JApiClass apiClass = (JApiClass) member;
            CtClass oldClass = apiClass.oldClass.get()
            CtClass newClass = apiClass.newClass.get()

            List<Boolean> oldTypeParametersNullability = typeParametersNullabilityOf(oldClass)
            List<Boolean> newTypeParametersNullability = typeParametersNullabilityOf(newClass)

            for (int idx = 0; idx < oldTypeParametersNullability.size(); idx++) {
                def oldNullability = oldTypeParametersNullability[idx]
                def newNullability = newTypeParametersNullability[idx]
                if (oldNullability != newNullability) {
                    errors << "Type parameter $idx nullability changed, might be a breaking change depending on its usage".toString()
                }
            }

        } else if (member instanceof JApiField) {

            JApiField field = (JApiField) member
            CtField oldField = field.oldFieldOptional.get()
            CtField newField = field.newFieldOptional.get()

            def oldNullability = nullabilityOf(oldField)
            def newNullability = nullabilityOf(newField)

            if (Modifier.isFinal(oldField.modifiers) && Modifier.isFinal(newField.modifiers)) {
                if (!oldNullability && newNullability) {
                    errors << "From non-nullable to nullable breaking change"
                } else if (oldNullability && !newNullability) {
                    warnings << "Nullability changed from nullable to non-nullable"
                }
            } else if (oldNullability != newNullability) {
                errors << "Nullability breaking change"
            }

        } else if (member instanceof JApiConstructor) {

            JApiConstructor ctor = (JApiConstructor) member
            inspectParametersNullabilityOf(ctor.oldConstructor.get(), ctor.newConstructor.get())

        } else if (member instanceof JApiMethod) {

            JApiMethod method = (JApiMethod) member
            CtMethod oldMethod = method.oldMethod.get()
            CtMethod newMethod = method.newMethod.get()

            inspectParametersNullabilityOf(oldMethod, newMethod)

            def oldNullability = nullabilityOf(oldMethod)
            def newNullability = nullabilityOf(newMethod)

            if (oldNullability == Nullability.NON_NULL && newNullability == Nullability.NULLABLE) {
                errors << "From non-null returning to null returning breaking change"
            } else if (oldNullability == Nullability.NON_NULL && newNullability == Nullability.UNMARKED) {
                errors << "From non-null returning to null-unmarked returning breaking change"
            } else if (oldNullability == Nullability.UNMARKED && newNullability == Nullability.NULLABLE) {
                errors << "From null-unmarked returning to null returning breaking change"
            } else if (oldNullability == Nullability.UNMARKED && newNullability == Nullability.NON_NULL) {
                warnings << "Return nullability changed from null-unmarked to non-nullable"
            } else if (oldNullability == Nullability.NULLABLE && newNullability == Nullability.NON_NULL) {
                warnings << "Return nullability changed from nullable to non-nullable"
            } else if (oldNullability == Nullability.NULLABLE && newNullability == Nullability.UNMARKED) {
                warnings << "Return nullability changed from nullable to null-unmarked"
            }
        }

        if (!errors.isEmpty()) {
            def changes = errors + warnings
            return acceptOrReject(member, changes, Violation.error(member, changes.join(" ")))
        }
        if (!warnings.isEmpty()) {
            return Violation.warning(member, warnings.join(" "))
        }
        return null
    }

    private static List<Boolean> typeParametersNullabilityOf(CtClass ctClass) {
        NullableClassTypeParametersVisitor visitor = new NullableClassTypeParametersVisitor()
        new ClassReader(byteCodeFrom(ctClass)).accept(visitor, 0)
        return visitor.typeParametersNullability
    }

    static class NullableClassTypeParametersVisitor extends ClassVisitor {

        List<Boolean> typeParametersNullability = []

        NullableClassTypeParametersVisitor() {
            super(AsmConstants.ASM_LEVEL)
        }

        @Override
        void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if (signature != null) {
                new SignatureReader(signature).accept(new SignatureVisitor(AsmConstants.ASM_LEVEL) {
                    @Override
                    void visitFormalTypeParameter(String formalTypeParameterName) {
                        typeParametersNullability.add(false)
                    }
                })
            }
        }

        @Override
        AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            TypeReference typeReference = new TypeReference(typeRef)
            if ((typeReference.getSort() == TypeReference.CLASS_TYPE_PARAMETER || typeReference.getSort() == TypeReference.CLASS_TYPE_PARAMETER_BOUND) &&
                NULLABLE_ANNOTATIONS.contains(Type.getType(descriptor).getClassName())) {
                typeParametersNullability.set(typeReference.getTypeParameterIndex(), true)
            }
            return null
        }
    }

    private static boolean nullabilityOf(CtField field) {
        NullableFieldVisitor visitor = new NullableFieldVisitor(field.getName())
        new ClassReader(byteCodeFrom(field.getDeclaringClass())).accept(visitor, 0)
        return visitor.nullable
    }

    static class NullableFieldVisitor extends ClassVisitor {

        boolean nullable = false
        private final String fieldName

        NullableFieldVisitor(String fieldName) {
            super(AsmConstants.ASM_LEVEL)
            this.fieldName = fieldName
        }

        @Override
        FieldVisitor visitField(int access, String name, String fieldDescriptor, String signature, Object value) {
            if (fieldName == name) {
                return new FieldVisitor(AsmConstants.ASM_LEVEL) {
                    @Override
                    AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (NULLABLE_ANNOTATIONS.contains(Type.getType(descriptor).getClassName())) {
                            nullable = true
                        }
                        return null
                    }

                    @Override
                    AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        if (new TypeReference(typeRef).getSort() == TypeReference.FIELD &&
                            NULLABLE_ANNOTATIONS.contains(Type.getType(descriptor).getClassName())) {
                            nullable = true
                        }
                        return null
                    }
                }
            }
            return null
        }
    }

    private static Nullability nullabilityOf(CtBehavior behavior) {
        NullableMethodVisitor visitor = new NullableMethodVisitor(behavior)
        new ClassReader(byteCodeFrom(behavior.getDeclaringClass())).accept(visitor, 0)
        return visitor.nullability
    }

    static class NullableMethodVisitor extends ClassVisitor {
        Nullability nullability = Nullability.NON_NULL
        private final CtBehavior behavior
        private final String behaviorName

        NullableMethodVisitor(CtBehavior behavior) {
            super(AsmConstants.ASM_LEVEL)
            this.behavior = behavior
            this.behaviorName = behavior instanceof CtConstructor ? "<init>" : behavior.getName()
        }

        @Override
        AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            handleAnnotation(descriptor)
            return null
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String methodDescriptor, String signature, String[] exceptions) {
            if (behaviorName == name && methodDescriptor == behavior.getSignature()) {
                return new MethodVisitor(AsmConstants.ASM_LEVEL) {

                    @Override
                    AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        handleAnnotation(descriptor)
                        return null
                    }

                    @Override
                    AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        if (new TypeReference(typeRef).getSort() == TypeReference.METHOD_RETURN) {
                            handleAnnotation(descriptor)
                        }
                        return null
                    }
                }
            }
            return null
        }

        private void handleAnnotation(String descriptor) {
            def annotationClassName = Type.getType(descriptor).getClassName()
            if (NULL_UNMARKED_ANNOTATION == annotationClassName) {
                nullability = Nullability.UNMARKED
            }
            if (NULLABLE_ANNOTATIONS.contains(annotationClassName)) {
                nullability = Nullability.NULLABLE
            }
        }
    }


    private static List<Boolean> parametersNullabilityOf(CtBehavior behavior) {
        NullableParametersVisitor visitor = new NullableParametersVisitor(behavior)
        new ClassReader(byteCodeFrom(behavior.getDeclaringClass())).accept(visitor, 0)
        return visitor.parametersNullability
    }

    static class NullableParametersVisitor extends ClassVisitor {

        private final CtBehavior behavior
        private final String behaviorName
        private Integer parametersOffset = 0
        List<Boolean> parametersNullability = null

        NullableParametersVisitor(CtBehavior behavior) {
            super(AsmConstants.ASM_LEVEL)
            this.behavior = behavior
            this.behaviorName = behavior instanceof CtConstructor ? "<init>" : behavior.getName()
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String methodDescriptor, String signature, String[] exceptions) {
            if (name == behaviorName && methodDescriptor == behavior.getSignature()) {
                Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor)
                parametersNullability = new ArrayList<>(argumentTypes.length)
                for (Type ignored : argumentTypes) {
                    parametersNullability.add(false)
                }
                return new MethodVisitor(AsmConstants.ASM_LEVEL) {

                    @Override
                    void visitAnnotableParameterCount(int parameterCount, boolean visible) {
                        parametersOffset = argumentTypes.length - parameterCount
                    }

                    @Override
                    AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                        int parameterIndex = parameter + parametersOffset
                        if (NULLABLE_ANNOTATIONS.contains(Type.getType(descriptor).getClassName())) {
                            parametersNullability.set(parameterIndex, true)
                        }
                        return null
                    }

                    @Override
                    AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                        TypeReference typeReference = new TypeReference(typeRef)
                        if (typeReference.getSort() == TypeReference.METHOD_FORMAL_PARAMETER &&
                            NULLABLE_ANNOTATIONS.contains(Type.getType(descriptor).getClassName())) {
                            int parameterIndex = typeReference.getFormalParameterIndex() + parametersOffset
                            parametersNullability.set(parameterIndex, true)
                        }
                        return null
                    }
                }
            }
            return null
        }
    }

    private static byte[] byteCodeFrom(CtClass ctClass) {
        def bos = new ByteArrayOutputStream()
        ctClass.classFile2.write(new DataOutputStream(bos))
        return bos.toByteArray()
    }
}
