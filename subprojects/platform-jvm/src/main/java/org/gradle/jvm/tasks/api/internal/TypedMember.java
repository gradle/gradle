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

package org.gradle.jvm.tasks.api.internal;

import com.google.common.collect.ComparisonChain;

public abstract class TypedMember extends AnnotatableMember {

    private final String typeDesc;

    public TypedMember(int access, String name, String signature, String typeDesc) {
        super(access, name, signature);
        this.typeDesc = typeDesc;
    }

    public String getTypeDesc() {
        return typeDesc;
    }

    protected ComparisonChain compare(TypedMember o) {
        return super.compare(o)
            .compare(typeDesc == null ? "" : typeDesc, o.typeDesc == null ? "" : o.typeDesc);
    }
}
