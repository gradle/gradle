/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.tasks.apigen.abi;

import com.google.common.collect.*;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MethodSig implements Comparable<MethodSig> {
    private final int access;
    private final String name;
    private final String desc;
    private final String signature;
    private final Set<String> exceptions;
    private final List<AnnotationSig> annotations = Lists.newArrayList();
    private final List<AnnotationSig> parameterAnnotations = Lists.newArrayList();

    public MethodSig(int access, String name, String desc, String signature, String[] exceptions) {
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.signature = signature;
        this.exceptions = exceptions == null ? Collections.<String>emptySet() : Sets.newTreeSet(ImmutableList.copyOf(exceptions));
    }

    public int getAccess() {
        return access;
    }

    public String getDesc() {
        return desc;
    }

    public Set<String> getExceptions() {
        return exceptions;
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    public List<AnnotationSig> getAnnotations() {
        return annotations;
    }

    public List<AnnotationSig> getParameterAnnotations() {
        return parameterAnnotations;
    }

    public AnnotationSig addAnnotation(String desc, boolean visible) {
        AnnotationSig sig = new AnnotationSig(desc, visible);
        annotations.add(sig);
        return sig;
    }
    public ParameterAnnotationSig addParameterAnnotation(int param, String desc, boolean visible) {
        ParameterAnnotationSig sig = new ParameterAnnotationSig(desc, visible, param);
        parameterAnnotations.add(sig);
        return sig;
    }

    @Override
    public int compareTo(MethodSig o) {
        return ComparisonChain.start()
            .compare(access, o.access)
            .compare(name, o.name)
            .compare(desc == null ? "" : desc, o.desc == null ? "" : o.desc)
            .compare(signature == null ? "" : signature, o.signature == null ? "" : o.signature)
            .compare(exceptions, o.exceptions, Ordering.<String>natural().lexicographical())
            .result();
    }

    @Override
    public String toString() {
        StringBuilder methodDesc = new StringBuilder();
        methodDesc.append(Modifier.toString(access)).append(" ");
        methodDesc.append(Type.getReturnType(desc).getClassName()).append(" ");
        methodDesc.append(name);
        methodDesc.append("(");
        Type[] argumentTypes = Type.getArgumentTypes(desc);
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
