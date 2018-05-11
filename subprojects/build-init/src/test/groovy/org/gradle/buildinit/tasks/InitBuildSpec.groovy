/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildinit.tasks

import org.gradle.api.GradleException
import org.gradle.buildinit.plugins.internal.BuildInitTypeIds
import org.gradle.buildinit.plugins.internal.ProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.NONE
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.SPOCK

@UsesNativeServices
class InitBuildSpec extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()

    InitBuild init

    ProjectLayoutSetupRegistry projectLayoutRegistry

    ProjectInitDescriptor projectSetupDescriptor

    def setup() {
        init = TestUtil.create(testDir.testDirectory).task(InitBuild)
        projectLayoutRegistry = Mock(ProjectLayoutSetupRegistry.class)
        projectSetupDescriptor = Mock(ProjectInitDescriptor.class)
        init.projectLayoutRegistry = projectLayoutRegistry
    }

    def "throws GradleException if requested setupDescriptor not supported"() {
        setup:
        _ * projectLayoutRegistry.get("aType") >> null
        _ * projectLayoutRegistry.getSupportedTypes() >> ['supported-type', 'another-supported-type']
        init.type = "aType"
        when:
        init.setupProjectLayout()
        then:
        def e = thrown(GradleException)
        e.message == "The requested build setup type 'aType' is not supported. Supported types: 'supported-type', 'another-supported-type'."
    }

    def "delegates task action to referenced setupDescriptor"() {
        given:
        supportedType(BuildInitTypeIds.BASIC, projectSetupDescriptor)

        when:
        init.setupProjectLayout()

        then:
        1 * projectSetupDescriptor.generate(GROOVY, NONE)
    }

    def "should delegate to setup descriptor with specified type and modifier"() {
        given:
        supportedType(BuildInitTypeIds.JAVA_LIBRARY, projectSetupDescriptor)
        projectSetupDescriptor.supports(SPOCK) >> true
        init.type = "java-library"
        init.testFramework = "spock"

        when:
        init.setupProjectLayout()

        then:
        1 * projectSetupDescriptor.generate(GROOVY, SPOCK)
    }

    def "should throw exception if requested test framework is not supported"() {
        given:
        supportedType(BuildInitTypeIds.BASIC, projectSetupDescriptor)
        init.testFramework = "unknown"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "The requested test framework 'unknown' is not supported."
    }

    def "should throw exception if requested test framework is not supported for the specified type"() {
        given:
        supportedType(BuildInitTypeIds.BASIC, projectSetupDescriptor)
        projectSetupDescriptor.supports(SPOCK) >> false
        init.testFramework = "spock"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "The requested test framework 'spock' is not supported in 'basic' setup type"
    }

    def "should throw exception if requested DSL is not supported for the specified type"() {
        given:
        supportedType(BuildInitTypeIds.POM, projectSetupDescriptor)
        projectSetupDescriptor.supports(KOTLIN) >> false
        init.type = BuildInitTypeIds.POM
        init.dsl = "kotlin"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "The requested DSL 'kotlin' is not supported in 'pom' setup type"
    }

    private void supportedType(String type, BuildInitDsl dsl = GROOVY, ProjectInitDescriptor projectSetupDescriptor) {
        projectSetupDescriptor.supports(dsl) >> true
        projectLayoutRegistry.supports(type) >> true
        projectLayoutRegistry.get(type) >> projectSetupDescriptor
    }
}
