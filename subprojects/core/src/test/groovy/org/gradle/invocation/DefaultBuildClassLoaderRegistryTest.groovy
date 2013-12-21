/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.invocation

import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import spock.lang.Specification

class DefaultBuildClassLoaderRegistryTest extends Specification {
    final globalRegistry = Mock(ClassLoaderRegistry)

    def "caches root compile scope"() {
        def rootClassLoader = Mock(ClassLoader)

        given:
        globalRegistry.gradleApiClassLoader >> rootClassLoader
        def registry = new DefaultBuildClassLoaderRegistry(globalRegistry)

        expect:
        registry.rootCompileScope == registry.rootCompileScope
    }

    def "wires up build classloaders"() {
        def rootClassLoader = Mock(ClassLoader)
        def additionalClassLoader = Mock(ClassLoader)

        given:
        globalRegistry.gradleApiClassLoader >> rootClassLoader

        when:
        def registry = new DefaultBuildClassLoaderRegistry(globalRegistry)
        def rootCompileScope = registry.rootCompileScope

        then:
        rootCompileScope.scriptCompileClassLoader instanceof CachingClassLoader
        rootCompileScope.scriptCompileClassLoader.parent instanceof MultiParentClassLoader
        rootCompileScope.scriptCompileClassLoader.parent.parents == [rootClassLoader]

        when:
        registry.addRootClassLoader(additionalClassLoader)

        then:
        rootCompileScope.scriptCompileClassLoader.parent.parents == [rootClassLoader, additionalClassLoader]
    }
}
