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

package org.gradle.internal.instrumentation.processor.modelreader.impl;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import org.objectweb.asm.Type;

import javax.annotation.Nonnull;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeUtils {
    public static Type extractType(TypeMirror typeMirror) {
        return typeMirror.accept(new TypeMirrorToType(), null);
    }

    public static Type extractRawType(TypeName typeName) {
        if (typeName.isPrimitive()) {
            if (typeName.equals(TypeName.BOOLEAN)) {
                return Type.BOOLEAN_TYPE;
            }
            if (typeName.equals(TypeName.BYTE)) {
                return Type.BYTE_TYPE;
            }
            if (typeName.equals(TypeName.SHORT)) {
                return Type.SHORT_TYPE;
            }
            if (typeName.equals(TypeName.INT)) {
                return Type.INT_TYPE;
            }
            if (typeName.equals(TypeName.LONG)) {
                return Type.LONG_TYPE;
            }
            if (typeName.equals(TypeName.CHAR)) {
                return Type.CHAR_TYPE;
            }
            if (typeName.equals(TypeName.FLOAT)) {
                return Type.FLOAT_TYPE;
            }
            if (typeName.equals(TypeName.DOUBLE)) {
                return Type.DOUBLE_TYPE;
            }
        } else if (typeName.equals(TypeName.VOID)) {
            return Type.VOID_TYPE;
        }

        ClassName className;
        if (typeName instanceof ParameterizedTypeName) {
            className = ((ParameterizedTypeName) typeName).rawType;
        } else if (typeName instanceof ClassName) {
            className = (ClassName) typeName;
        } else {
            throw new IllegalArgumentException("Not supported to extract raw type from: " + typeName.getClass());
        }
        return Type.getType("L" + className.reflectionName().replace('.', '/') + ";");
    }

    public static Type extractReturnType(ExecutableElement methodElement) {
        return extractType(methodElement.getReturnType());
    }

    public static String extractMethodDescriptor(ExecutableElement methodElement) {
        return Type.getMethodDescriptor(
            extractReturnType(methodElement),
            methodElement.getParameters().stream().map(it -> extractType(it.asType())).toArray(Type[]::new)
        );
    }

    public static Optional<TypeName> getTypeParameter(TypeMirror typeMirror, int index) {
        if (typeMirror instanceof DeclaredType && ((DeclaredType) typeMirror).getTypeArguments().size() > index) {
            return Optional.of(TypeName.get(((DeclaredType) typeMirror).getTypeArguments().get(index)));
        }
        return Optional.empty();
    }

    public static TypeName getTypeParameterOrThrow(TypeMirror typeMirror, int index) {
        return getTypeParameter(typeMirror, index).orElseThrow(() -> new IllegalArgumentException(String.format("Missing type parameter with index %s for %s", index, typeMirror)));
    }

    @Nonnull
    public static List<ExecutableElement> getExecutableElementsFromElements(Stream<? extends Element> elements) {
        return elements
            .flatMap(element -> element.getKind() == ElementKind.METHOD ? Stream.of(element) : element.getEnclosedElements().stream())
            .filter(it -> it.getKind() == ElementKind.METHOD)
            .map(it -> (ExecutableElement) it)
            // Ensure that the elements have a stable order, as the annotation processing engine does not guarantee that for type elements.
            // The order in which the executable elements are listed should be the order in which they appear in the code but
            // we take an extra measure of care here and ensure the ordering between all elements.
            .sorted(Comparator.comparing(TypeUtils::elementQualifiedName))
            .distinct()
            .collect(Collectors.toList());
    }

    public static String elementQualifiedName(Element element) {
        if (element instanceof ExecutableElement) {
            String enclosingTypeName = ((TypeElement) element.getEnclosingElement()).getQualifiedName().toString();
            return enclosingTypeName + "." + element.getSimpleName();
        } else if (element instanceof TypeElement) {
            return ((TypeElement) element).getQualifiedName().toString();
        } else {
            throw new IllegalArgumentException("Unsupported element type to read qualified name from: " + element.getClass());
        }
    }
}
