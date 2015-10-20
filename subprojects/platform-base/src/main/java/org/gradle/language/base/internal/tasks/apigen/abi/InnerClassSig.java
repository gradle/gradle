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

public class InnerClassSig implements Comparable<InnerClassSig> {
    private final String name;
    private final String outerName;
    private final String innerName;
    private final int access;

    public InnerClassSig(String name, String outerName, String innerName, int access) {
        this.name = name;
        this.outerName = outerName;
        this.innerName = innerName;
        this.access = access;
    }

    public int getAccess() {
        return access;
    }

    public String getInnerName() {
        return innerName;
    }

    public String getName() {
        return name;
    }

    public String getOuterName() {
        return outerName;
    }

    @Override
    public int compareTo(InnerClassSig o) {
        return ComparisonChain.start()
            .compare(access, o.access)
            .compare(name, o.name)
            .compare(outerName == null ? "" : outerName, o.outerName == null ? "" : o.outerName)
            .compare(innerName == null ? "" : innerName, o.innerName == null ? "" : o.innerName)
            .result();
    }
}
