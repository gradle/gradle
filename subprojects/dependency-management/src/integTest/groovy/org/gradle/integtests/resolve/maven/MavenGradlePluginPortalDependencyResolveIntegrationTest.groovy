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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.ONLINE)
class MavenGradlePluginPortalDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "resolves dependencies on real projects"() {
        given:
        buildFile << """
repositories {
    gradlePluginPortal()
}

configurations {
    compile
}

dependencies {
    compile "commons-collections:commons-collections:3.0"
    compile "ch.qos.logback:logback-classic:0.9.30"
    compile "org.hibernate:hibernate-core:3.6.7.Final"
}

task check << {
    def compile = configurations.compile
    assert compile.resolvedConfiguration.firstLevelModuleDependencies.collect { it.name } == [
        'ch.qos.logback:logback-classic:0.9.30',
        'org.hibernate:hibernate-core:3.6.7.Final',
        'commons-collections:commons-collections:3.1'
    ]

    def filteredDependencies = compile.resolvedConfiguration.getFirstLevelModuleDependencies({ it.name == 'logback-classic' } as Spec)
    assert filteredDependencies.collect { it.name } == [
        'ch.qos.logback:logback-classic:0.9.30'
    ]

    def filteredFiles = compile.resolvedConfiguration.getFiles({ it.name == 'logback-classic' } as Spec)
    assert filteredFiles.collect { it.name } == [
        'logback-classic-0.9.30.jar',
         'logback-core-0.9.30.jar',
          'slf4j-api-1.6.2.jar'
    ]

    assert compile.collect { it.name } == [
        'logback-classic-0.9.30.jar',
        'hibernate-core-3.6.7.Final.jar',
        'logback-core-0.9.30.jar',
        'slf4j-api-1.6.2.jar',
        'antlr-2.7.6.jar',
        'dom4j-1.6.1.jar',
        'hibernate-commons-annotations-3.2.0.Final.jar',
        'hibernate-jpa-2.0-api-1.0.1.Final.jar',
        'jta-1.1.jar',
        'commons-collections-3.1.jar'
    ]

    assert compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == [
        'logback-classic-0.9.30.jar',
        'hibernate-core-3.6.7.Final.jar',
        'logback-core-0.9.30.jar',
        'slf4j-api-1.6.2.jar',
        'antlr-2.7.6.jar',
        'dom4j-1.6.1.jar',
        'hibernate-commons-annotations-3.2.0.Final.jar',
        'hibernate-jpa-2.0-api-1.0.1.Final.jar',
        'jta-1.1.jar',
        'commons-collections-3.1.jar'
    ]
}

task repoNames << {
    println repositories*.name
}
"""

        expect:
        succeeds "check", "repoNames"

        and:
        output.contains(["GradlePluginPortal"].toString())
    }
}
