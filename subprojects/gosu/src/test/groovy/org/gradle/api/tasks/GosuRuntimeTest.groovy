/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.api.plugins.gosu.GosuBasePlugin
import org.gradle.util.TestUtil
import org.gradle.util.VersionNumber
import spock.lang.Specification

class GosuRuntimeTest extends Specification {

    def project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(GosuBasePlugin)
    }

    def "inferred Gosu classpath contains 'gosu-ant-tools' repository dependency matching 'gosu-core-api' Jar found on classpath"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def classpath = project.gosuRuntime.inferGosuClasspath([new File("other.jar"), new File("gosu-core-api-1.13.1.jar")])

        then:
        classpath instanceof LazilyInitializedFileCollection
        classpath.sourceCollections.size() == 1
        with(classpath.sourceCollections[0]) {
            it instanceof Configuration
            it.state == Configuration.State.UNRESOLVED
            it.dependencies.size() == 1
            with(it.dependencies.iterator().next()) {
                group == 'org.gosu-lang.gosu'
                name == 'gosu-ant-tools'
                version == '1.13.1'
            }
        }
    }

    def "inference fails if configuration is empty and no repository declared"() {
        when:
        def gosuClasspath = project.gosuRuntime.inferGosuClasspath([new File("other.jar"), new File("gosu-core-api-1.13.1.jar")])
        gosuClasspath.files

        then:
        GradleException e = thrown()
        e.message == 'Cannot infer Gosu classpath because no repository is declared in ' + project
    }

    def "inference fails if configuration is empty and no Gosu Jar is found on classpath"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def gosuClasspath = project.gosuRuntime.inferGosuClasspath([new File("other.jar"), new File("other2.jar")])
        gosuClasspath.files

        then:
        GradleException e = thrown()
        e.message.startsWith('Cannot infer Gosu classpath because the Gosu Core API Jar was not found.')
    }

    def 'test to find Gosu Jars on class path'() {
        when:
        def core = project.gosuRuntime.findGosuJar([new File('other.jar'), new File('gosu-core-1.7.jar'), new File('gosu-core-api-1.8.jar')], 'core')
        def coreApi = project.gosuRuntime.findGosuJar([new File('other.jar'), new File('gosu-core-1.7.jar'), new File('gosu-core-api-1.8.jar')], 'core-api')

        then:
        core.name == 'gosu-core-1.7.jar'
        coreApi.name == 'gosu-core-api-1.8.jar'
    }

    def "returns null if Gosu API Jar not found"() {
        when:
        def file = project.gosuRuntime.findGosuJar([new File("other.jar"), new File('gosu-core-api-1.8.jar')], 'core')

        then:
        file == null
    }

    def 'correctly determines version of a Gosu Jar'() {
        expect:
        with(project.gosuRuntime) {
            getGosuVersion(new File('gosu-core-1-spec-SNAPSHOT.jar')) == '1-spec-SNAPSHOT'
            getGosuVersion(new File('gosu-core-api-1.8.jar')) == '1.8'
            getGosuVersion(new File('gosu-xml-0.9-15-SNAPSHOT.jar')) == '0.9-15-SNAPSHOT'

            VersionNumber.parse(getGosuVersion(new File('gosu-core-1-spec-SNAPSHOT.jar'))).toString() == '1.0.0-spec-SNAPSHOT'
            VersionNumber.parse(getGosuVersion(new File('gosu-core-api-1.8.jar'))).toString() == '1.8.0'
            VersionNumber.parse(getGosuVersion(new File('gosu-xml-0.9-15-SNAPSHOT.jar'))).toString() == '0.9.0-15-SNAPSHOT'
        }
    }

    def "returns null if Gosu version cannot be determined"() {
        expect:
        with(project.gosuRuntime) {
            getGosuVersion(new File("gosu-ant-tools.jar")) == null
            getGosuVersion(new File("groovy-compiler-2.1.0.jar")) == null
        }
    }
}
