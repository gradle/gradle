/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java.internal

import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.sources.BaseLanguageSourceSet
import spock.lang.Specification

class DefaultJavaLanguageSourceSetTest extends Specification {

    Instantiator instantiator

    def setup() {
        instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), DirectInstantiator.INSTANCE)
    }

    def "can add a project dependency using dependencies property"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies.project ':foo'

        then:
        sourceSet.dependencies.dependencies.size() == 1
        sourceSet.dependencies.dependencies[0].projectPath == ':foo'
    }

    def "can add a project dependency"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies {
            project ':foo'
        }

        then:
        sourceSet.dependencies.dependencies.size() == 1
        sourceSet.dependencies.dependencies[0].projectPath == ':foo'
    }

    def "can add a library dependency"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies {
            library 'fooLib'
        }

        then:
        sourceSet.dependencies.dependencies.size() == 1
        sourceSet.dependencies.dependencies[0].libraryName == 'fooLib'
    }

    def "can add a project library dependency"() {
        def sourceSet = newJavaSourceSet()

        when:
        sourceSet.dependencies {
            project ':foo' library 'fooLib'
        }

        then:
        sourceSet.dependencies.dependencies.size() == 1
        sourceSet.dependencies.dependencies[0].projectPath == ':foo'
        sourceSet.dependencies.dependencies[0].libraryName == 'fooLib'
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
        sourceSet.dependencies.dependencies.size() == 3
        sourceSet.dependencies.dependencies[0].projectPath == ':foo'
        sourceSet.dependencies.dependencies[1].libraryName == 'fooLib'
        sourceSet.dependencies.dependencies[2].projectPath == ':bar'
        sourceSet.dependencies.dependencies[2].libraryName == 'barLib'
    }

    private DefaultJavaLanguageSourceSet newJavaSourceSet() {
        BaseLanguageSourceSet.create(DefaultJavaLanguageSourceSet, "javaX", "javaX", Stub(FileResolver), instantiator)
    }
}
