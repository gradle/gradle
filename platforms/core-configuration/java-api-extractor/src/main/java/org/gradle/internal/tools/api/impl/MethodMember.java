/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

public class MethodMember extends TypedMember implements Comparable<MethodMember> {
    private static final Ordering<Iterable<String>> LEXICOGRAPHICAL_ORDERING = Ordering.<String>natural().lexicographical();
    private final SortedSet<String> exceptions = new TreeSet<>();
    private final List<String> declaredExceptions;
    private final List<ParameterMember> parameters = new ArrayList<>();
    private final SortedSet<AnnotationMember> parameterAnnotations = new TreeSet<>();
    @Nullable
    private AnnotationValue<?> annotationDefaultValue;

    public MethodMember(int access, String name, String typeDesc, @Nullable String signature, String @Nullable [] exceptions) {
        super(access, name, signature, typeDesc);
        this.declaredExceptions = exceptions == null ? Collections.emptyList() : Arrays.asList(exceptions);
        this.exceptions.addAll(declaredExceptions);
    }

    public SortedSet<String> getExceptions() {
        return ImmutableSortedSet.copyOf(exceptions);
    }

    /**
     * The entries of the {@code MethodParameters} attribute, in declaration order.
     */
    public List<ParameterMember> getParameters() {
        return ImmutableList.copyOf(parameters);
    }

    public void addParameter(ParameterMember parameter) {
        parameters.add(parameter);
    }

    /**
     * Maps a type reference from the declaration order of the thrown exceptions to the sorted
     * order in which this member writes them.
     *
     * <p>The {@code type_index} of a {@code THROWS} type annotation points into the
     * {@code Exceptions} attribute. Since the extracted class writes the exceptions sorted, the
     * index of the original class file no longer identifies the annotated exception. Type
     * references of any other sort are returned unchanged.</p>
     */
    public int mapTypeReferenceToWrittenExceptionOrder(int typeRef) {
        TypeReference typeReference = new TypeReference(typeRef);
        if (typeReference.getSort() != TypeReference.THROWS) {
            return typeRef;
        }
        int declaredIndex = typeReference.getExceptionIndex();
        if (declaredIndex < 0 || declaredIndex >= declaredExceptions.size()) {
            return typeRef;
        }
        String exception = declaredExceptions.get(declaredIndex);
        return TypeReference.newExceptionReference(exceptions.headSet(exception).size()).getValue();
    }

    public SortedSet<AnnotationMember> getParameterAnnotations() {
        return ImmutableSortedSet.copyOf(parameterAnnotations);
    }

    public void addParameterAnnotation(ParameterAnnotationMember parameterAnnotationMember) {
        parameterAnnotations.add(parameterAnnotationMember);
    }

    public Optional<@Nullable AnnotationValue<?>> getAnnotationDefaultValue() {
        return Optional.ofNullable(annotationDefaultValue);
    }

    public void setAnnotationDefaultValue(AnnotationValue<?> annotationDefaultValue) {
        this.annotationDefaultValue = annotationDefaultValue;
    }

    @Override
    public int compareTo(MethodMember o) {
        return super.compare(o)
            .compare(exceptions, o.exceptions, LEXICOGRAPHICAL_ORDERING)
            .result();
    }

    @Override
    public String toString() {
        StringBuilder methodDesc = new StringBuilder();
        methodDesc.append(Modifier.toString(getAccess())).append(" ");
        methodDesc.append(Type.getReturnType(getTypeDesc()).getClassName()).append(" ");
        methodDesc.append(getName());
        methodDesc.append("(");
        Type[] argumentTypes = Type.getArgumentTypes(getTypeDesc());
        for (int i = 0, argumentTypesLength = argumentTypes.length; i < argumentTypesLength; i++) {
            Type type = argumentTypes[i];
            methodDesc.append(type.getClassName());
            if (i < argumentTypesLength - 1) {
                methodDesc.append(", ");
            }
        }
        methodDesc.append(")");
        return methodDesc.toString();
    }
}
