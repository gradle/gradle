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

import org.gradle.internal.instrumentation.api.annotations.CallableDefinition;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import com.google.common.collect.ImmutableList;
import java.util.List;

@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME)
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

    /**
     * Make sure that there is at least one interceptor declaration so the classes are generated and the integration works.
     * TODO this interceptor may be removed once others are added
     */
    @InterceptCalls
    @CallableKind.StaticMethod(ofClass = InterceptorDeclaration.class)
    @CallableDefinition.Name("emptyStubToEnsureClassGeneration")
    public static void stub() {
    }
}

