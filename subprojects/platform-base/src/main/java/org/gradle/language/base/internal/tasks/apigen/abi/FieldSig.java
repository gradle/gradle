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

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.List;

public class FieldSig implements Comparable<FieldSig> {
    private final int access;
    private final String name;
    private final String desc;
    private final String signature;
    private final List<AnnotationSig> annotations = Lists.newArrayList();

    public FieldSig(int access, String name, String desc, String signature) {
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.signature = signature;
    }

    public int getAccess() {
        return access;
    }

    public String getDesc() {
        return desc;
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

    public AnnotationSig addAnnotation(String desc, boolean visible) {
        AnnotationSig sig = new AnnotationSig(desc, visible);
        annotations.add(sig);
        return sig;
    }

    @Override
    public int compareTo(FieldSig o) {
        return ComparisonChain.start()
            .compare(access, o.access)
            .compare(name, o.name)
            .compare(desc == null ? "" : desc, o.desc == null ? "" : o.desc)
            .compare(signature == null ? "" : signature, o.signature == null ? "" : o.signature)
            .result();
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", Modifier.toString(access), Type.getType(desc).getClassName(), name);
    }
}
