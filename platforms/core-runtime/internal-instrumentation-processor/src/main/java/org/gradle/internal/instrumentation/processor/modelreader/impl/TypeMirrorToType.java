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

import org.objectweb.asm.Type;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TypeMirrorToType extends AbstractTypeVisitor8<Type, Void> {

    @Override
    public Type visitPrimitive(PrimitiveType t, Void unused) {
        TypeKind kind = t.getKind();
        if (kind == TypeKind.INT) {
            return Type.INT_TYPE;
        }
        if (kind == TypeKind.BYTE) {
            return Type.BYTE_TYPE;
        }
        if (kind == TypeKind.BOOLEAN) {
            return Type.BOOLEAN_TYPE;
        }
        if (kind == TypeKind.DOUBLE) {
            return Type.DOUBLE_TYPE;
        }
        if (kind == TypeKind.FLOAT) {
            return Type.FLOAT_TYPE;
        }
        if (kind == TypeKind.LONG) {
            return Type.LONG_TYPE;
        }
        if (kind == TypeKind.SHORT) {
            return Type.SHORT_TYPE;
        }
        if (kind == TypeKind.VOID) {
            return Type.VOID_TYPE;
        }
        throw unsupportedType(t);
    }

    @Override
    public Type visitNoType(NoType t, Void unused) {
        return Type.VOID_TYPE;
    }

    @Override
    public Type visitNull(NullType t, Void unused) {
        throw unsupportedType(t);
    }

    @Override
    public Type visitArray(ArrayType t, Void unused) {
        return Type.getObjectType("[" + visit(t.getComponentType(), null).getDescriptor());
    }

    @Override
    public Type visitDeclared(DeclaredType t, Void unused) {
        List<Element> typeNesting = new ArrayList<>();
        Element current = t.asElement();
        while (current != null) {
            typeNesting.add(current);
            current = current.getEnclosingElement();
        }
        Collections.reverse(typeNesting);

        // TODO: replace with javax.lang.model.util.Elements.getBinaryName, which is a more universal way but requires refactoring and passing the utility around
        StringBuilder typeName = new StringBuilder("L");
        typeNesting.forEach(element -> {
            if (element instanceof PackageElement) {
                typeName.append(((PackageElement) element).getQualifiedName().toString().replace(".", "/")).append("/");
            } else {
                typeName.append(element.getSimpleName().toString());
                if (element != typeNesting.get(typeNesting.size() - 1)) {
                    typeName.append("$");
                }
            }
        });
        typeName.append(";");

        return Type.getType(typeName.toString());
    }

    @Override
    public Type visitError(ErrorType t, Void unused) {
        throw unsupportedType(t);
    }

    @Override
    public Type visitTypeVariable(TypeVariable t, Void unused) {
        throw unsupportedType(t);
    }

    @Override
    public Type visitWildcard(WildcardType t, Void unused) {
        throw unsupportedType(t);
    }

    @Override
    public Type visitExecutable(ExecutableType t, Void unused) {
        throw unsupportedType(t);
    }

    @Override
    public Type visitIntersection(IntersectionType t, Void unused) {
        throw unsupportedType(t);
    }

    @Override
    public Type visitUnion(UnionType t, Void unused) {
        throw unsupportedType(t);
    }

    private static UnsupportedOperationException unsupportedType(TypeMirror t) {
        return new UnsupportedOperationException("unsupported type " + t);
    }
}
