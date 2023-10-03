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

package org.gradle.internal.classpath

import org.gradle.internal.classpath.intercept.CallInterceptorResolver
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import static org.gradle.internal.classpath.Instrumented.CallInterceptorRegistry
import static org.gradle.internal.classpath.Instrumented.CallInterceptorLoader

class CallInterceptorLoaderTest extends Specification {

    @RestoreSystemProperties
    def "should filter upgraded properties interceptors"() {
        def jvmInterceptors = CallInterceptorLoader.loadJvmBytecodeInterceptors(CallInterceptorLoaderTest.classLoader)
        def groovyInterceptors = CallInterceptorLoader.loadGroovyCallInterceptors(CallInterceptorLoaderTest.classLoader)

        expect:
        getJvmInterceptorsNames(jvmInterceptors).contains("org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesJvmBytecode_CoreTestInterceptors")
        ((CallInterceptorResolver) groovyInterceptors).isAwareOfCallSiteName("getTestFilterInterceptors")

        when:
        System.setProperty(CallInterceptorRegistry.PROPERTY_UPGRADES_DISABLED, "true")
        jvmInterceptors = CallInterceptorLoader.loadJvmBytecodeInterceptors(CallInterceptorLoaderTest.classLoader)
        groovyInterceptors = CallInterceptorLoader.loadGroovyCallInterceptors(CallInterceptorLoaderTest.classLoader)

        then:
        !getJvmInterceptorsNames(jvmInterceptors).contains("org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesJvmBytecode_CoreTestInterceptors")
        !((CallInterceptorResolver) groovyInterceptors).isAwareOfCallSiteName("getTestFilterInterceptors")
    }

    private static Set<String> getJvmInterceptorsNames(JvmBytecodeInterceptorSet jvmInterceptors) {
        return jvmInterceptors.getInterceptors(null, null).collect { it.class.name } as Set
    }
}
