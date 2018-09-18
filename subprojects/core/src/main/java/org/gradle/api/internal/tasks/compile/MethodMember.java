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

package org.gradle.api.internal.tasks.compile;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.SortedSet;

public class MethodMember extends TypedMember implements Comparable<MethodMember> {
    private static final Ordering<Iterable<String>> LEXICOGRAPHICAL_ORDERING = Ordering.<String>natural().lexicographical();
    private final SortedSet<String> exceptions = Sets.newTreeSet();
    private final SortedSet<AnnotationMember> parameterAnnotations = Sets.newTreeSet();

    public MethodMember(int access, String name, String typeDesc, String signature, String[] exceptions) {
        super(access, name, signature, typeDesc);
        if (exceptions != null && exceptions.length > 0) {
            this.exceptions.addAll(Arrays.asList(exceptions));
        }
    }

    public SortedSet<String> getExceptions() {
        return ImmutableSortedSet.copyOf(exceptions);
    }

    public SortedSet<AnnotationMember> getParameterAnnotations() {
        return ImmutableSortedSet.copyOf(parameterAnnotations);
    }

    public void addParameterAnnotation(ParameterAnnotationMember parameterAnnotationMember) {
        parameterAnnotations.add(parameterAnnotationMember);
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
