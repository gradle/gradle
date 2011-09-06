/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal

import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.classpath.ModuleRegistry
import spock.lang.Specification

class DependencyClassPathProviderTest extends Specification {
    final ModuleRegistry moduleRegistry = Mock()
    final DependencyClassPathProvider provider = new DependencyClassPathProvider(moduleRegistry)

    def "uses modules to determine gradle API classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_API")

        then:
        classpath.collect{it.name} == ["gradle-core", "runtime.jar", "gradle-core-impl", "gradle-plugins"]

        and:
        1 * moduleRegistry.getModule("gradle-core") >> module("gradle-core")
        1 * moduleRegistry.getModule("gradle-core-impl") >> module("gradle-core-impl")
        1 * moduleRegistry.getModule("gradle-plugins") >> module("gradle-plugins")
    }

    def module(String name) {
        Module module = Mock()
        _ * module.classpath >> [new File(name), new File("runtime.jar")]
        return module
    }
}
