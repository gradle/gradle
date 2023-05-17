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

package org.gradle.internal.instrumentation.api.declarations;

import java.util.Collections;
import java.util.List;

public class InterceptorDeclaration {
    public static final String JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE = "org.gradle.internal.classpath.generated.InterceptorDeclaration_ConfigCacheJvmBytecode";
    public static final String GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE = "org.gradle.internal.classpath.generated.InterceptorDeclaration_ConfigCacheGroovyInterceptors";

    /**
     * Not yet used in production. Disabled for now since it causes some performance regressions.
     */
    public static final String JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES = "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesJvmBytecode";
    /**
     * Not yet used in production. Disabled for now since it causes some performance regressions.
     */
    public static final String GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES = "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesGroovyInterceptors";

    public static final List<String> JVM_BYTECODE_GENERATED_CLASS_NAMES = Collections.singletonList(
        JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE
    );

    public static final List<String> GROOVY_INTERCEPTORS_GENERATED_CLASS_NAMES = Collections.singletonList(
        GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE
    );
}
