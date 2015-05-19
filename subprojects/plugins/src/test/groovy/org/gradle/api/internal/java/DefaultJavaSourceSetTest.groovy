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
package org.gradle.api.internal.java
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.jvm.Classpath
import org.gradle.platform.base.DependencySpecContainer
import org.gradle.platform.base.internal.DefaultDependencySpecContainer
import spock.lang.Specification

class DefaultJavaSourceSetTest extends Specification {
    def "has useful String representation"() {
        def sourceSet = newJavaSourceSet()

        expect:
        sourceSet.displayName == "Java source 'mainX:javaX'"
        sourceSet.toString() == "Java source 'mainX:javaX'"
    }

    def "can add a project dependency using dependencies property"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies.project ':foo'

        then:
        sourceSet.dependencies.size() == 1
        sourceSet.dependencies[0].projectPath == ':foo'
    }

    def "can add a project dependency"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies {
            project ':foo'
        }

        then:
        sourceSet.dependencies.size() == 1
        sourceSet.dependencies[0].projectPath == ':foo'
    }

    def "can add a library dependency"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies {
            library 'fooLib'
        }

        then:
        sourceSet.dependencies.size() == 1
        sourceSet.dependencies[0].libraryName == 'fooLib'
    }

    def "can add a project library dependency"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies {
            project ':foo' library 'fooLib'
        }

        then:
        sourceSet.dependencies.size() == 1
        sourceSet.dependencies[0].projectPath == ':foo'
        sourceSet.dependencies[0].libraryName == 'fooLib'
    }

    def "can add a multiple dependencies"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies {
            project ':foo'
            library 'fooLib'
            project ':bar' library 'barLib'
        }

        then:
        sourceSet.dependencies.size() == 3
        sourceSet.dependencies[0].projectPath == ':foo'
        sourceSet.dependencies[1].libraryName == 'fooLib'
        sourceSet.dependencies[2].projectPath == ':bar'
        sourceSet.dependencies[2].libraryName == 'barLib'
    }

    private DefaultJavaSourceSet newJavaSourceSet() {
        new DefaultJavaSourceSet("javaX", "mainX", Stub(SourceDirectorySet), Stub(Classpath), new DefaultDependencySpecContainer()) {
            DependencySpecContainer dependencies(Closure config) {
                dependencies(new ClosureBackedAction<DependencySpecContainer>(config))
            }
        }
    }
}
