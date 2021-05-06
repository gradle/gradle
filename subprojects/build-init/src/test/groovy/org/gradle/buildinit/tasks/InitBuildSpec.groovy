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
import org.gradle.buildinit.plugins.internal.BuildConverter
import org.gradle.buildinit.plugins.internal.BuildInitializer
import org.gradle.buildinit.plugins.internal.InitSettings
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.TextUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.JUNIT
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.NONE
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework.SPOCK

@UsesNativeServices
class InitBuildSpec extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())

    InitBuild init

    ProjectLayoutSetupRegistry projectLayoutRegistry

    BuildInitializer projectSetupDescriptor
    BuildConverter buildConverter

    def setup() {
        init = TestUtil.create(testDir.testDirectory).task(InitBuild)
        projectLayoutRegistry = Mock(ProjectLayoutSetupRegistry.class)
        projectSetupDescriptor = Mock(BuildInitializer.class)
        buildConverter = Mock(BuildConverter.class)
        init.projectLayoutRegistry = projectLayoutRegistry
    }

    def "creates project with all defaults"() {
        given:
        projectLayoutRegistry.buildConverter >> buildConverter
        buildConverter.canApplyToCurrentDirectory() >> false
        projectLayoutRegistry.default >> projectSetupDescriptor
        projectLayoutRegistry.getLanguagesFor(ComponentType.BASIC) >> [Language.NONE]
        projectLayoutRegistry.get(ComponentType.BASIC, Language.NONE) >> projectSetupDescriptor
        projectSetupDescriptor.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        projectSetupDescriptor.componentType >> ComponentType.BASIC
        projectSetupDescriptor.dsls >> [GROOVY]
        projectSetupDescriptor.defaultDsl >> GROOVY
        projectSetupDescriptor.testFrameworks >> [NONE]
        projectSetupDescriptor.defaultTestFramework >> NONE
        projectSetupDescriptor.getFurtherReading(_ as InitSettings) >> Optional.empty()

        when:
        init.setupProjectLayout()

        then:
        1 * projectSetupDescriptor.generate({it.dsl == GROOVY && it.testFramework == NONE})
    }

    def "creates project with specified type and dsl and test framework"() {
        given:
        projectLayoutRegistry.get("java-library") >> projectSetupDescriptor
        projectSetupDescriptor.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        projectSetupDescriptor.testFrameworks >> [SPOCK]
        projectSetupDescriptor.dsls >> [GROOVY, KOTLIN]
        projectSetupDescriptor.getFurtherReading(_ as InitSettings) >> Optional.empty()
        projectSetupDescriptor.componentType >> ComponentType.LIBRARY
        init.type = "java-library"
        init.dsl = "kotlin"
        init.testFramework = "spock"

        when:
        init.setupProjectLayout()

        then:
        1 * projectSetupDescriptor.generate({it.dsl == KOTLIN && it.testFramework == SPOCK})
    }

    def "should throw exception if requested test framework is not supported for the specified type"() {
        given:
        projectLayoutRegistry.get("some-type") >> projectSetupDescriptor
        projectSetupDescriptor.id >> "some-type"
        projectSetupDescriptor.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        projectSetupDescriptor.dsls >> [GROOVY]
        projectSetupDescriptor.testFrameworks >> [NONE, JUNIT]
        init.type = "some-type"
        init.testFramework = "spock"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""The requested test framework 'spock' is not supported for 'some-type' build type. Supported frameworks:
  - 'none'
  - 'junit'""")
    }

    def "should throw exception if requested DSL is not supported for the specified type"() {
        given:
        projectLayoutRegistry.get("some-type") >> projectSetupDescriptor
        projectSetupDescriptor.id >> "some-type"
        projectSetupDescriptor.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        projectSetupDescriptor.dsls >> [GROOVY]
        init.type = "some-type"
        init.dsl = "kotlin"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "The requested DSL 'kotlin' is not supported for 'some-type' build type"
    }

    def "should throw exception if project name is not supported for the specified type"() {
        given:
        projectLayoutRegistry.get("some-type") >> projectSetupDescriptor
        projectSetupDescriptor.id >> "some-type"
        projectSetupDescriptor.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        projectSetupDescriptor.dsls >> [GROOVY]
        projectSetupDescriptor.testFrameworks >> [NONE]
        projectSetupDescriptor.supportsProjectName()
        init.type = "some-type"
        init.projectName = "other"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "Project name is not supported for 'some-type' build type."
    }

    def "should throw exception if package name is not supported for the specified type"() {
        given:
        projectLayoutRegistry.get("some-type") >> projectSetupDescriptor
        projectSetupDescriptor.id >> "some-type"
        projectSetupDescriptor.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        projectSetupDescriptor.dsls >> [GROOVY]
        projectSetupDescriptor.testFrameworks >> [NONE]
        projectSetupDescriptor.supportsPackage()
        init.type = "some-type"
        init.packageName = "other"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "Package name is not supported for 'some-type' build type."
    }

}
