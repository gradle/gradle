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

package org.gradle.internal.classpath.transforms;

import org.jspecify.annotations.NullMarked;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Type.getType;

/**
 * A collection of common types and method descriptors used when instrumenting bytecode.
 */
@NullMarked
final class CommonTypes {
    public static final Type OBJECT_TYPE = getType(Object.class);
    public static final Type STRING_TYPE = getType(String.class);
    public static final String[] NO_EXCEPTIONS = new String[0];

    private CommonTypes() {
    }
}
