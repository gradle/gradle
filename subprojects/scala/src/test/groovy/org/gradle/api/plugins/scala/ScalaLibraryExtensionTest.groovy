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
package org.gradle.api.plugins.scala

import org.gradle.jvm.tasks.Jar
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class ScalaLibraryExtensionTest extends Specification {

    def project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(ScalaPlugin)
    }

    @Unroll
    def "determines the binary version #binaryVersion from library #libraryVersion"(String libraryVersion, String binaryVersion) {
        when:
        project.scala.version = libraryVersion
        then:
        project.scala.binaryVersion == binaryVersion
        where:
        libraryVersion << [ '2.10.4', '2.10.5', '2.11.0', '2.12.0' ]
        binaryVersion  << [ '2.10',   '2.10',   '2.11',   '2.12' ]
    }

    def "additional components are added as compile dependencies"() {
        given:
        project.scala.version = '2.10.5'
        project.scala.additionalComponents = ['xml']
        when:
        project.evaluate()
        then:
        project.configurations.compile.dependencies.matching { it.group == 'org.scala-lang' && it.name == 'scala-xml' && it.version == '2.10.5' }
    }

    def "scala-library is added as a compile dependency"() {
        given:
        project.scala.version = '2.10.5'
        when:
        project.evaluate()
        then:
        project.configurations.compile.dependencies.matching { it.group == 'org.scala-lang' && it.name == 'scala-library' && it.version == '2.10.5' }
    }

    def "scala binary version is appended to artifact name"() {
        given:
        project.scala.version = '2.11.0'
        when:
        project.evaluate()
        then:
        project.tasks.withType(Jar) {
            assert(it.baseName.endsWith('_2.11'))
        }
    }

    def "publishVersion prevents appending of scala binary version to artifact name"() {
        given:
        project.scala.version = '2.11.0'
        project.scala.publishVersion = false
        when:
        project.evaluate()
        then:
        project.tasks.withType(Jar) {
            assert(it.baseName == project.name)
        }
    }
}