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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.Property;
import org.gradle.internal.classpath.declarations.InterceptorDeclaration;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;

import java.lang.reflect.InvocationTargetException;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CODE_QUALITY)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CODE_QUALITY)
public class CheckstyleInterceptor {

    @InterceptCalls
    @SuppressWarnings("unchecked")
    @CallableKind.InstanceMethod
    public static int intercept_getMaxErrors(@ParameterKind.Receiver Checkstyle self) {
        // return self.getMaxErrors().getOrElse(0);
        try {
            Property<Integer> property = (Property<Integer>) self.getClass().getMethod("getMaxErrors").invoke(self);
            return property.getOrElse(0);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @InterceptCalls
    @SuppressWarnings("unchecked")
    @CallableKind.InstanceMethod
    public static void intercept_setMaxErrors(@ParameterKind.Receiver Checkstyle self, int value) {
        // self.getMaxErrors().set(value);
        try {
            Property<Integer> property = (Property<Integer>) self.getClass().getMethod("getMaxErrors").invoke(self);
            property.set(value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
