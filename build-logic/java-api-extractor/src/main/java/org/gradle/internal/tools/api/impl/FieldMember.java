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

import com.google.common.collect.Ordering;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;

public class FieldMember extends TypedMember implements Comparable<FieldMember> {

    private final Object value;

    public FieldMember(int access, String name, String signature, String typeDesc, Object value) {
        super(access, name, signature, typeDesc);
        this.value = value;
    }

    @Override
    public int compareTo(FieldMember o) {
        return super.compare(o).compare(value, o.value, Ordering.arbitrary()).result();
    }

    @Override
    public String toString() {
        return String.format(
            "%s %s %s", Modifier.toString(getAccess()), Type.getType(getTypeDesc()).getClassName(), getName());
    }

    public Object getValue() {
        return value;
    }
}
