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

package org.gradle.initialization;

import com.google.common.collect.ImmutableList;
import kotlin.Metadata;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.JvmName;
import kotlin.jvm.JvmStatic;
import kotlin.reflect.KClass;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.internal.DelegatingConvention;
import org.gradle.model.internal.asm.ClassVisitorScope;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Collection;

import static org.gradle.internal.classpath.transforms.CommonTypes.OBJECT_TYPE;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Generates the bytecode for Convention interface, implementation classes, and Kotlin extensions.
 */
public class ConventionInterfaceGenerator {
    public static final Type KCLASS_TYPE = getType(KClass.class);
    public static final Type CONVENTION_TYPE = Type.getObjectType("org/gradle/api/plugins/Convention");
    public static final String CONVENTION_INTERFACE_CLASS_NAME = CONVENTION_TYPE.getClassName();
    public static final Type CLASS_TYPE = getType(Class.class);
    public static final String RETURN_CLASS_FROM_KCLASS = getMethodDescriptor(CLASS_TYPE, KCLASS_TYPE);
    public static final String RETURN_CLASS_FROM_OBJECT = getMethodDescriptor(CLASS_TYPE, OBJECT_TYPE);
    public static final String RETURN_OBJECT_FROM_CLASS = getMethodDescriptor(OBJECT_TYPE, CLASS_TYPE);
    public static final String RETURN_OBJECT_FROM_CONVENTION_KCLASS = getMethodDescriptor(OBJECT_TYPE, CONVENTION_TYPE, KCLASS_TYPE);
    public static final String CONVENTION_INTERFACE_NAME = CONVENTION_TYPE.getInternalName();
    public static final String EXTENSION_CONTAINER_DELEGATE_IMPLEMENTATION_NAME = getType(DelegatingConvention.class).getInternalName();
    public static final String EXTENSION_CONTAINER_CLASS_NAME = getType(ExtensionContainer.class).getInternalName();
    public static final String CONSTRUCTOR_DESCRIPTOR = "(L" + EXTENSION_CONTAINER_CLASS_NAME + ";)V";
    public static final String CONSTRUCTOR_NAME = "<init>";
    public static final String FIND_PLUGIN = "findPlugin";
    public static final String RETURN_GENERIC_FROM_CLASS = "<T:Ljava/lang/Object;>(Ljava/lang/Class<TT;>;)TT;";
    public static final Type JVM_CLASS_MAPPING_TYPE = getType(JvmClassMappingKt.class);
    public static final String DEFAULT_DECORATED_CONVENTION_NAME = "org.gradle.api.plugins.internal.DefaultDecoratedConvention";
    public static final String CONVENTION_EXTENSIONS_KT_NAME = "org.gradle.kotlin.dsl.ConventionExtensionsKt";
    public static final ImmutableList<String> REMOVED_CLASSES = ImmutableList.of(
        CONVENTION_INTERFACE_CLASS_NAME,
        DEFAULT_DECORATED_CONVENTION_NAME,
        CONVENTION_EXTENSIONS_KT_NAME);
    private static final int JAVA_BYTE_CODE_COMPATIBILITY = Opcodes.V1_8;
    private static final int INTERFACE_ACCESS_MODIFIERS = ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_INTERFACE | ACC_ABSTRACT;

    /**
     * Determines if the class is a Convention-related class.
     */
    public boolean isConventionClass(String name) {
        return REMOVED_CLASSES.contains(name);
    }

    /**
     * Generates the appropriate implementation for Convention-related classes.
     */
    public byte[] generateConventionClass(String name) {
        if (name.equals(CONVENTION_INTERFACE_CLASS_NAME)) {
            return generateConventionInterface();
        } else if (name.equals(DEFAULT_DECORATED_CONVENTION_NAME)) {
            return generateConventionImplementation(name);
        } else if (name.equals(CONVENTION_EXTENSIONS_KT_NAME)) {
            return generateKotlinExtensionClass(name);
        }
        throw new IllegalArgumentException("Unknown Convention class: " + name);
    }

    /**
     * Generates the Convention interface implementation.
     */
    public byte[] generateConventionImplementation(String className) {
        String internalName = getInternalName(className);

        // Create class writer with automatic computation of frames and max stack/locals
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        new ClassVisitorScope(classWriter) {{
            // Start class definition
            visit(
                JAVA_BYTE_CODE_COMPATIBILITY,
                ACC_PUBLIC,
                internalName,
                null,
                EXTENSION_CONTAINER_DELEGATE_IMPLEMENTATION_NAME,
                new String[]{
                    CONVENTION_INTERFACE_NAME
                }
            );

            // Create constructor that passes ExtensionContainer to superclass
            publicMethod(
                CONSTRUCTOR_NAME,
                CONSTRUCTOR_DESCRIPTOR,
                null,
                methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                    visitCode();
                    // Load 'this'
                    _ALOAD(0);
                    // Load the ExtensionContainer parameter
                    _ALOAD(1);
                    // Call superclass constructor
                    _INVOKESPECIAL(
                        EXTENSION_CONTAINER_DELEGATE_IMPLEMENTATION_NAME,
                        CONSTRUCTOR_NAME,
                        CONSTRUCTOR_DESCRIPTOR
                    );
                    _RETURN();
                }}
            );
            visitEnd();
        }};

        return classWriter.toByteArray();
    }

    /**
     * Generates the Convention interface.
     */
    private byte[] generateConventionInterface() {
        // Create class writer
        ClassWriter classWriter = new ClassWriter(0);
        new ClassVisitorScope(classWriter) {{
            // Start class definition
            visit(
                JAVA_BYTE_CODE_COMPATIBILITY,
                INTERFACE_ACCESS_MODIFIERS,
                getInternalName(CONVENTION_INTERFACE_CLASS_NAME),
                null,
                OBJECT_TYPE.getInternalName(),
                new String[]{
                    EXTENSION_CONTAINER_CLASS_NAME
                }
            );

            // Add the findByType method
            // Method signature: <T> T findByType(org.gradle.api.reflect.TypeOf<T> type)
            publicAbstractMethod(
                "findByType",
                RETURN_CLASS_FROM_OBJECT,
                "<T:Ljava/lang/Object;>(Lorg/gradle/api/reflect/TypeOf<TT;>;)TT;",
                methodVisitor -> {}
            );

            // Add the findPlugin method
            publicAbstractMethod(
                FIND_PLUGIN,
                RETURN_OBJECT_FROM_CLASS,
                RETURN_GENERIC_FROM_CLASS,
                methodVisitor -> {}
            );

            visitEnd();
        }};

        return classWriter.toByteArray();
    }

    /**
     * Generates the Kotlin extension class for Convention.
     */
    public byte[] generateKotlinExtensionClass(String className) {
        String internalName = getInternalName(className);

        // Create class writer with automatic computation of frames and max stack/locals
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        new ClassVisitorScope(classWriter) {{
            // Start class definition
            visit(
                JAVA_BYTE_CODE_COMPATIBILITY,
                ACC_PUBLIC | Opcodes.ACC_FINAL,
                internalName,
                null,
                OBJECT_TYPE.getInternalName(),
                null
            );

            // Add @JvmName annotation to the class (optional, for Kotlin interop)
            AnnotationVisitor classAnnotation = visitAnnotation(JvmName.class);
            classAnnotation.visit("name", internalName.substring(internalName.lastIndexOf('/') + 1));
            classAnnotation.visitEnd();

            // Add @Metadata annotation required by Kotlin
            AnnotationVisitor metadataAnnotation = visitAnnotation(Metadata.class);
            // These values are usually generated by the Kotlin compiler
            // We're providing minimal values to make it work
            metadataAnnotation.visit("k", 1); // Kind = 1 (File)
            metadataAnnotation.visit("mv", new int[]{1, 8, 0}); // Metadata version
            metadataAnnotation.visitEnd();

            publicMethod(CONSTRUCTOR_NAME, "()V", null, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                visitCode();
                _ALOAD(0);
                _INVOKESPECIAL(OBJECT_TYPE, CONSTRUCTOR_NAME, "()V", false);
                _RETURN();
            }});

            // Create the extension function as a static method
            publicStaticMethod(
                FIND_PLUGIN,
                RETURN_OBJECT_FROM_CONVENTION_KCLASS,
                "<T:Ljava/lang/Object;>(L" + CONVENTION_INTERFACE_NAME + ";Lkotlin/reflect/KClass<TT;>;)TT;",
                mv -> new MethodVisitorScope(mv) {{
                    visitAnnotation(Nullable.class).visitEnd();
                    visitAnnotation(JvmStatic.class).visitEnd();

                    visitCode();

                    // Load the Convention parameter (first parameter)
                    _ALOAD(0);
                    // Load the KClass parameter (second parameter)
                    _ALOAD(1);

                    _INVOKESTATIC(JVM_CLASS_MAPPING_TYPE, "getJavaClass", RETURN_CLASS_FROM_KCLASS);
                    // Call Convention.findPlugin(Class)
                    _INVOKEINTERFACE(CONVENTION_TYPE, FIND_PLUGIN, RETURN_OBJECT_FROM_CLASS);
                    _ARETURN();

                    // Add JvmName annotation to the method (optional, for Kotlin interop)
                    AnnotationVisitor methodNameAnnotation = visitAnnotation(JvmName.class);
                    methodNameAnnotation.visit("name", FIND_PLUGIN);
                    methodNameAnnotation.visitEnd();
                }}
            );

            visitEnd();
        }};

        return classWriter.toByteArray();
    }

    private static String getInternalName(String className) {
        return className.replace('.', '/');
    }

    public Collection<String> getRemovedClassNames() {
        return REMOVED_CLASSES;
    }
}
