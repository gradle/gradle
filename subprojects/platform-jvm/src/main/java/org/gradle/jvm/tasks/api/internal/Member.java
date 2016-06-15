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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Models a single element of a codebase that may be inspected and acted upon with
 * bytecode manipulation libraries tools like ASM.
 *
 * <p>The notion of "member" here is similar to, but broader than
 * {@link java.lang.reflect.Member}. The latter is essentially an abstraction over fields,
 * methods and constructors; this Member and its subtypes represent not only fields and
 * methods, but also classes, inner classes, annotations and their values, and more. This
 * model is minimalistic and has a few assumptions about being used in an ASM context, but
 * provides us in any case with what we need to effectively find and manipulate API
 * members, construct API classes out of them, and ultimately to assemble an
 * {@link org.gradle.jvm.tasks.api.ApiJar}.</p>
 */
public abstract class Member {

    private final String name;

    public Member(String name) {
        this.name = checkNotNull(name);
    }

    public String getName() {
        return name;
    }

    protected ComparisonChain compare(Member o) {
        return ComparisonChain.start().compare(name, o.name);
    }
}
