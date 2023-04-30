/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.NonNullApi;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAMES;

@NonNullApi
public interface GroovyCallInterceptorsProvider {

    GroovyCallInterceptorsProvider DEFAULT = () -> Stream.concat(
        Stream.of(Instrumented.class.getName()),
        GROOVY_INTERCEPTORS_GENERATED_CLASS_NAMES.stream()
    ).collect(Collectors.toList());

    List<String> getInterceptorProviderClassNames();

    default GroovyCallInterceptorsProvider plus(GroovyCallInterceptorsProvider other) {
        return () -> Stream.concat(getInterceptorProviderClassNames().stream(), other.getInterceptorProviderClassNames().stream()).collect(Collectors.toList());
    }

    static GroovyCallInterceptorsProvider fromClass(Class<?> theClass) {
        return () -> Collections.singletonList(theClass.getName());
    }

    static GroovyCallInterceptorsProvider fromClassName(String className) {
        return () -> Collections.singletonList(className);
    }
}
