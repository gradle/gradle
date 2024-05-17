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
package org.gradle.connectivity

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.Online)
class MavenJcenterDependencyResolveIntegrationTest extends AbstractIntegrationSpec {
    def "resolves a minimal dependency from bintray's jcenter"() {
        given:
        executer.expectDocumentedDeprecationWarning("The RepositoryHandler.jcenter() method has been deprecated. This is scheduled to be removed in Gradle 9.0. JFrog announced JCenter's sunset in February 2021. Use mavenCentral() instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#jcenter_deprecation")
        executer.expectDocumentedDeprecationWarning("The RepositoryHandler.jcenter(Action<MavenArtifactRepository>) method has been deprecated. This is scheduled to be removed in Gradle 9.0. JFrog announced JCenter's sunset in February 2021. Use mavenCentral(Action<MavenArtifactRepository> instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#jcenter_deprecation")
        buildFile << """
repositories {
    jcenter()
    jcenter { // just test this syntax works.
        name = "otherJcenter"
        content {
            includeGroup 'org.sample'
        }
    }
}

configurations {
    compile
}

dependencies {
    compile "ch.qos.logback:logback-classic:1.0.13"
}

task check {
    doLast {
        def compile = configurations.compile
        assert compile.resolvedConfiguration.firstLevelModuleDependencies.collect { it.name } == [
            'ch.qos.logback:logback-classic:1.0.13',
        ]

        assert compile.collect { it.name } == [
            'logback-classic-1.0.13.jar',
            'logback-core-1.0.13.jar',
            'slf4j-api-1.7.5.jar'
        ]

        assert compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == [
            'logback-classic-1.0.13.jar',
            'logback-core-1.0.13.jar',
            'slf4j-api-1.7.5.jar'
        ]
    }
}

task repoNames {
    doLast {
        println repositories*.name
    }
}
"""

        expect:
        succeeds "check", "repoNames"

        and:
        output.contains(["BintrayJCenter", "otherJcenter"].toString())
    }
}
