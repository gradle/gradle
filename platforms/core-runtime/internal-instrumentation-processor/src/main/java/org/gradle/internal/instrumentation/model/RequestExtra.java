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

package org.gradle.internal.instrumentation.model;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.gradle.internal.instrumentation.api.capabilities.BytecodeUpgradeInterceptor;

import javax.lang.model.element.ExecutableElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.gradle.internal.instrumentation.model.RequestExtra.InterceptionType.CONFIGURATION_CACHE_INSTRUMENTATION;

public interface RequestExtra {

    enum InterceptionType {
        CONFIGURATION_CACHE_INSTRUMENTATION,
        BYTECODE_UPGRADE(ClassName.get(BytecodeUpgradeInterceptor.class))
        ;

        /**
         * Marks capabilities of interception class.
         * This adds a marker interface to the generated class, that can be used to filter interceptor.
         */
        private final Set<TypeName> capabilities;

        InterceptionType(TypeName... capabilities) {
            this.capabilities = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(capabilities)));
        }

        public Set<TypeName> getCapabilities() {
            return capabilities;
        }
    }

    interface HasInterceptionType {
        InterceptionType getInterceptionType();
    }

    class InterceptJvmCalls implements RequestExtra, HasInterceptionType {
        private final String implementationClassName;

        private final InterceptionType interceptionType;

        public InterceptJvmCalls(String implementationClassName) {
            this(implementationClassName, CONFIGURATION_CACHE_INSTRUMENTATION);
        }

        public InterceptJvmCalls(String implementationClassName, InterceptionType interceptionType) {
            this.implementationClassName = implementationClassName;
            this.interceptionType = interceptionType;
        }

        public String getImplementationClassName() {
            return implementationClassName;
        }

        public InterceptionType getInterceptionType() {
            return interceptionType;
        }
    }

    class InterceptGroovyCalls implements RequestExtra, HasInterceptionType {
        private final String implementationClassName;

        private final InterceptionType interceptionType;

        public InterceptGroovyCalls(String implementationClassName) {
            this(implementationClassName, CONFIGURATION_CACHE_INSTRUMENTATION);
        }

        public InterceptGroovyCalls(String implementationClassName, InterceptionType interceptionType) {
            this.implementationClassName = implementationClassName;
            this.interceptionType = interceptionType;
        }

        public String getImplementationClassName() {
            return implementationClassName;
        }

        public InterceptionType getInterceptionType() {
            return interceptionType;
        }
    }

    class OriginatingElement implements RequestExtra {
        private final ExecutableElement element;

        public ExecutableElement getElement() {
            return element;
        }

        public OriginatingElement(ExecutableElement element) {
            this.element = element;
        }
    }
}
