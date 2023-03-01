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

package org.gradle.internal.classpath.declarations;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class InterceptorDeclaration {
    public static final String JVM_BYTECODE_GENERATED_CLASS_NAME = "org.gradle.internal.classpath.InterceptorDeclaration_JvmBytecodeImpl";
    public static final String JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CODE_QUALITY = JVM_BYTECODE_GENERATED_CLASS_NAME + "CodeQuality";
    public static final String GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME = "org.gradle.internal.classpath.InterceptorDeclaration_GroovyInterceptorsImpl";
    public static final String GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CODE_QUALITY = GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME + "CodeQuality";

    public static final List<String> JVM_BYTECODE_GENERATED_CLASS_NAMES = ImmutableList.of(
        JVM_BYTECODE_GENERATED_CLASS_NAME,
        JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CODE_QUALITY
    );

    public static final List<String> GROOVY_INTERCEPTORS_GENERATED_CLASS_NAMES = ImmutableList.of(
        GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME,
        GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CODE_QUALITY
    );
}

