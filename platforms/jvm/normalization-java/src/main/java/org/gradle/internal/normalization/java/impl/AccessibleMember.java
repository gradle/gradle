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

package org.gradle.internal.normalization.java.impl;

import com.google.common.collect.ComparisonChain;

public abstract class AccessibleMember extends Member {

    private final int access;

    public AccessibleMember(int access, String name) {
        super(name);
        this.access = access;
    }

    public int getAccess() {
        return access;
    }

    protected ComparisonChain compare(AccessibleMember o) {
        return super.compare(o).compare(access, o.access);
    }
}
