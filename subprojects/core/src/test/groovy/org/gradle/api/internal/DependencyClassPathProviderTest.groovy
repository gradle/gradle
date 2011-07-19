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

import spock.lang.Specification
import org.gradle.initialization.ClassLoaderRegistry

class DependencyClassPathProviderTest extends Specification {
    final ClassLoaderRegistry classLoaderRegistry = Mock()

    def "uses classpath resource to determine gradle api classpath"() {
        given:
        _ * classLoaderRegistry.coreImplClassLoader >> getClass().classLoader

        expect:
        def provider = new DependencyClassPathProvider(classLoaderRegistry)
        def classpath = provider.findClassPath("GRADLE_API")
        classpath.find { it.name.matches('slf4j-.+\\.jar') }
    }
}
