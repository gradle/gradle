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

package org.gradle.internal.classpath;

import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.InterceptInherited;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;

import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS;
import static org.gradle.internal.classpath.BasicCallInterceptionTestInterceptorsDeclaration.JVM_BYTECODE_GENERATED_CLASS;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = JVM_BYTECODE_GENERATED_CLASS)
@SpecificGroovyCallInterceptors(generatedClassName = GROOVY_GENERATED_CLASS)
public class InheritedMethodsInterceptionTestInterceptorsDeclaration {

    @InterceptCalls
    @InterceptInherited
    @CallableKind.InstanceMethod
    public static String intercept_sayHello(@ParameterKind.Receiver InheritedMethodTestReceiver self) {
        return self.sayHello() + " from: " + self.getClass().getName().replace(self.getClass().getPackage().getName() + ".", "");
    }
}
