/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.scala

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ScalaLanguagePluginIntegrationTest extends AbstractIntegrationSpec{

    def "creates default scala language source sets"() {
        when:
        buildFile << """
    plugins {
        id 'jvm-component'
        id 'scala-lang'
    }
    model {
        components {
            myLib(JvmLibrarySpec)
        }
    }

    task check << {
        def myLib = componentSpecs.myLib
        assert myLib instanceof JvmLibrarySpec

        assert myLib.sources.size() == 2
        assert myLib.sources.scala instanceof ScalaLanguageSourceSet
        assert myLib.sources.resources instanceof JvmResourceSet

        assert sources as Set == myLib.sources as Set

        binaries.withType(JarBinarySpec) { jvmBinary ->
            assert jvmBinary.source == myLib.source
        }
    }
"""
        then:
        succeeds "check"

        and:
        !file("build").exists()
    }


}
