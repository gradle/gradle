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

package org.gradle.internal.instrumentation.processor.codegen.groovy;

import com.squareup.javapoet.ClassName;
import org.gradle.internal.instrumentation.api.capabilities.InterceptionType;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.ConstructorInterceptorSpec;
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.NamedCallableInterceptorSpec;
import org.gradle.util.internal.TextUtil;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class CallInterceptorSpecs {
    private final List<NamedCallableInterceptorSpec> namedRequests;
    private final List<ConstructorInterceptorSpec> constructorRequests;

    CallInterceptorSpecs(Collection<NamedCallableInterceptorSpec> namedRequests, Collection<ConstructorInterceptorSpec> constructorRequests) {
        this.namedRequests = new ArrayList<>(namedRequests);
        this.constructorRequests = new ArrayList<>(constructorRequests);
    }

    public List<NamedCallableInterceptorSpec> getNamedRequests() {
        return namedRequests;
    }

    public List<ConstructorInterceptorSpec> getConstructorRequests() {
        return constructorRequests;
    }

    interface CallInterceptorSpec {
        String getClassName();

        String getFullClassName();

        InterceptionType getCapabilities();

        List<CallInterceptionRequest> getRequests();

        class NamedCallableInterceptorSpec implements CallInterceptorSpec {

            private final String name;
            private final String className;
            private final String fullClassName;
            private final List<CallInterceptionRequest> requests;

            private NamedCallableInterceptorSpec(String name, String className, String fullClassName, List<CallInterceptionRequest> requests) {
                this.name = name;
                this.className = className;
                this.fullClassName = fullClassName;
                this.requests = requests;
            }


            public String getName() {
                return name;
            }

            @Override
            public String getClassName() {
                return className;
            }

            @Override
            public String getFullClassName() {
                return fullClassName;
            }

            @Override
            public InterceptionType getCapabilities() {
                return null;
            }

            @Override
            public List<CallInterceptionRequest> getRequests() {
                return requests;
            }

            public static NamedCallableInterceptorSpec of(String implementationName, String name) {
                String className = TextUtil.capitalize(name) + "CallInterceptor";
                String fullClassName = implementationName + "$" + className;
                return new NamedCallableInterceptorSpec(name, className, fullClassName, new ArrayList<>());
            }
        }

        class ConstructorInterceptorSpec implements CallInterceptorSpec {

            private final String fullClassName;
            private final Type constructorType;
            private final String className;
            private final List<CallInterceptionRequest> requests;

            private ConstructorInterceptorSpec(Type constructorType, String className, String fullClassName, List<CallInterceptionRequest> requests) {
                this.constructorType = constructorType;
                this.className = className;
                this.fullClassName = fullClassName;
                this.requests = requests;
            }

            public Type getConstructorType() {
                return constructorType;
            }

            @Override
            public String getClassName() {
                return className;
            }

            @Override
            public String getFullClassName() {
                return fullClassName;
            }

            @Override
            public InterceptionType getCapabilities() {
                return null;
            }

            @Override
            public List<CallInterceptionRequest> getRequests() {
                return requests;
            }

            public static ConstructorInterceptorSpec of(String implementationName, Type constructedType) {
                String className = ClassName.bestGuess(constructedType.getClassName()).simpleName() + "ConstructorCallInterceptor";
                String fullClassName = implementationName + "$" + className;
                return new ConstructorInterceptorSpec(constructedType, className, fullClassName, new ArrayList<>());
            }
        }
    }
}
