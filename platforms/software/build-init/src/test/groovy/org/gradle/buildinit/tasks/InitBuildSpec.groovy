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
//file:noinspection GroovyAccessibility

package org.gradle.buildinit.tasks

import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.userinput.UserQuestions
import org.gradle.buildinit.InsecureProtocolOption
import org.gradle.buildinit.plugins.internal.BuildConverter
import org.gradle.buildinit.plugins.internal.BuildGenerator
import org.gradle.buildinit.plugins.internal.BuildInitializer
import org.gradle.buildinit.plugins.internal.InitSettings
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

import static java.util.Optional.empty
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

    BuildGenerator defaultGenerator
    BuildConverter buildConverter

    def setup() {
        // Don't store userHome in the default location (in the project dir), because this will cause the non-empty project dir detection to fail
        init = TestUtil.create(testDir.testDirectory.file("project"), testDir.testDirectory.file("userHome")).task(InitBuild)
        projectLayoutRegistry = Mock()
        defaultGenerator = Mock()
        buildConverter = Mock()
        init.projectLayoutRegistry = projectLayoutRegistry
        init.insecureProtocol.convention(InsecureProtocolOption.WARN)
        init.useDefaults.convention(false)
        init.comments.convention(true)
        init.allowFileOverwrite.convention(false)
        init.projectDirectory.convention(init.layout.projectDirectory)
    }

    def "creates project with all defaults"() {
        given:
        projectLayoutRegistry.buildConverter >> buildConverter
        buildConverter.canApplyToCurrentDirectory() >> false
        projectLayoutRegistry.componentTypes >> ComponentType.values().toList()
        projectLayoutRegistry.defaultComponentType >> ComponentType.values().first()
        projectLayoutRegistry.default >> defaultGenerator
        projectLayoutRegistry.getGeneratorsFor(_) >> [defaultGenerator]
        defaultGenerator.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        defaultGenerator.dsls >> [KOTLIN]
        defaultGenerator.defaultDsl >> KOTLIN
        defaultGenerator.getTestFrameworks(_) >> [NONE]
        defaultGenerator.getDefaultTestFramework(_) >> NONE
        defaultGenerator.defaultProjectNames >> ["thing"]
        defaultGenerator.getFurtherReading(_ as InitSettings) >> empty()

        when:
        init.setupProjectLayout()

        then:
        1 * defaultGenerator.generate({ it.dsl == KOTLIN && it.testFramework == NONE })
    }

    def "creates project with specified type and dsl and test framework"() {
        given:
        projectLayoutRegistry.get("java-library") >> defaultGenerator
        defaultGenerator.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        defaultGenerator.getTestFrameworks(_) >> [SPOCK]
        defaultGenerator.dsls >> [GROOVY, KOTLIN]
        defaultGenerator.defaultProjectNames >> ["thing"]
        defaultGenerator.getFurtherReading(_ as InitSettings) >> empty()
        defaultGenerator.componentType >> ComponentType.LIBRARY
        init.type = "java-library"
        init.dsl = "kotlin"
        init.testFramework = "spock"

        when:
        init.setupProjectLayout()

        then:
        1 * defaultGenerator.generate({ it.dsl == KOTLIN && it.testFramework == SPOCK })
    }

    def "should throw exception if requested test framework is not supported for the specified type"() {
        given:
        projectLayoutRegistry.get("some-type") >> defaultGenerator
        defaultGenerator.id >> "some-type"
        defaultGenerator.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        defaultGenerator.dsls >> [KOTLIN]
        defaultGenerator.getTestFrameworks(_) >> [NONE, JUNIT]
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
        projectLayoutRegistry.get("some-type") >> defaultGenerator
        defaultGenerator.id >> "some-type"
        defaultGenerator.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        defaultGenerator.dsls >> [GROOVY]
        init.type = "some-type"
        init.dsl = "kotlin"

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "The requested DSL 'kotlin' is not supported for 'some-type' build type"
    }

    def "should use project name as specified"() {
        given:
        defaultGenerator.supportsProjectName() >> true
        init.projectName = "other"

        when:
        def projectName = init.getEffectiveProjectName(Mock(UserQuestions), defaultGenerator)

        then:
        projectName == "other"
    }

    def "should use project name from user input"() {
        given:
        defaultGenerator.supportsProjectName() >> true
        def userQuestions = Mock(UserQuestions)
        userQuestions.askQuestion("Project name", _ as String) >> "newProjectName"


        when:
        def projectName = init.getEffectiveProjectName(userQuestions, defaultGenerator)

        then:
        projectName == "newProjectName"
    }

    def "should use project directory name for project name if not explicitly provided"() {
        given:
        defaultGenerator.supportsProjectName() >> true
        init.projectDirectory.set(init.layout.projectDirectory.dir("other-dir"))
        def userQuestions = Mock(UserQuestions)
        // answer with default value
        userQuestions.askQuestion("Project name", _) >> { it[1] }

        when:
        def projectName = init.getEffectiveProjectName(userQuestions, defaultGenerator)

        then:
        projectName == "other-dir"
    }


    def "should throw exception if project name is not supported for the specified type"() {
        given:
        defaultGenerator.id >> "some-type"
        init.projectName = "invalidProjectName"

        when:
        init.getEffectiveProjectName(Mock(UserQuestions), defaultGenerator)

        then:
        GradleException e = thrown()
        e.message == "Project name is not supported for 'some-type' build type."
    }

    def "should throw exception if package name is not supported for the specified type"() {
        given:
        defaultGenerator.id >> "some-type"
        init.packageName = "other"

        when:
        init.getEffectivePackageName(defaultGenerator)

        then:
        GradleException e = thrown()
        e.message == "Package name is not supported for 'some-type' build type."
    }

    def "should use default package name if not specified"() {
        given:
        defaultGenerator.id >> "some-type"
        defaultGenerator.supportsPackage() >> true

        when:
        def packageName = init.getEffectivePackageName(defaultGenerator)

        then:
        packageName == "org.example"
    }

    def "should use package name as specified"() {
        given:
        defaultGenerator.id >> "some-type"
        defaultGenerator.supportsPackage() >> true
        init.packageName = "myPackageName"
        when:
        def packageName = init.getEffectivePackageName(defaultGenerator)

        then:
        packageName == "myPackageName"
    }

    def "get java language version for #language"() {
        given:
        def userQuestions = Mock(UserQuestions)
        userQuestions.askIntQuestion(_ as String, InitBuild.MINIMUM_VERSION_SUPPORTED_BY_FOOJAY_API, InitBuild.DEFAULT_JAVA_VERSION) >> 11
        def buildInitializer = Mock(BuildInitializer)
        buildInitializer.supportsJavaTargets() >> isJvmLanguage

        when:
        def languageVersion = init.getJavaLanguageVersion(userQuestions, buildInitializer)

        then:
        languageVersion == result

        where:
        language        | result                     | isJvmLanguage
        Language.JAVA   | JavaLanguageVersion.of(11) | true
        Language.SCALA  | JavaLanguageVersion.of(11) | true
        Language.KOTLIN | JavaLanguageVersion.of(11) | true
        Language.GROOVY | JavaLanguageVersion.of(11) | true
        Language.CPP    | null                       | false
        Language.SWIFT  | null                       | false
    }

    def "gets java-version from property"() {
        given:
        def userQuestions = Mock(UserQuestions)
        def buildInitializer = Mock(BuildInitializer)
        buildInitializer.supportsJavaTargets() >> true
        init.javaVersion = "11"

        when:
        def version = init.getJavaLanguageVersion(userQuestions, buildInitializer)

        then:
        version.asInt() == 11
    }

    def "gets useful error when requesting invalid Java target"() {
        given:
        def userQuestions = Mock(UserQuestions)
        def buildInitializer = Mock(BuildInitializer)
        buildInitializer.supportsJavaTargets() >> true

        init.getJavaVersion().set("invalid")

        when:
        init.getJavaLanguageVersion(userQuestions, buildInitializer)

        then:
        def e = thrown(GradleException)
        e.message == "Invalid target Java version 'invalid'. The version must be an integer."
    }

    def "gets useful error when requesting Java target below minimum"() {
        given:
        def userQuestions = Mock(UserQuestions)
        def buildInitializer = Mock(BuildInitializer)
        buildInitializer.supportsJavaTargets() >> true

        init.getJavaVersion().set("5")

        when:
        init.getJavaLanguageVersion(userQuestions, buildInitializer)

        then:
        def e = thrown(GradleException)
        e.message == "Target Java version: '5' is not a supported target version. It must be equal to or greater than 7"
    }

    def "should reject invalid package name: #invalidPackageName"() {
        given:
        projectLayoutRegistry.get("java-library") >> defaultGenerator
        defaultGenerator.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        defaultGenerator.testFrameworks >> [SPOCK]
        defaultGenerator.dsls >> [GROOVY]
        defaultGenerator.supportsPackage() >> true
        init.type = "java-library"
        init.packageName = invalidPackageName

        when:
        init.setupProjectLayout()

        then:
        GradleException e = thrown()
        e.message == "Package name: '" + invalidPackageName + "' is not valid - it may contain invalid characters or reserved words."

        where:
        invalidPackageName << [
            'some.new.thing',
            'my.package',
            '2rt9.thing',
            'a.class.of.mine',
            'if.twice.then.double',
            'custom.for.stuff',
            'th-is.isnt.legal',
            'nor.is.-.this',
            'nor.is._.this',
        ]
    }

    def "should allow unusual but valid package name: #validPackageName"() {
        given:
        projectLayoutRegistry.get("java-library") >> defaultGenerator
        defaultGenerator.modularizationOptions >> [ModularizationOption.SINGLE_PROJECT]
        defaultGenerator.getTestFrameworks(_) >> [SPOCK]
        defaultGenerator.dsls >> [GROOVY]
        defaultGenerator.getFurtherReading(_ as InitSettings) >> empty()
        defaultGenerator.getDefaultProjectNames() >> ["thing"]
        defaultGenerator.supportsPackage() >> true
        init.type = "java-library"
        init.dsl = "groovy"
        init.testFramework = "spock"
        init.packageName = validPackageName

        when:
        init.setupProjectLayout()

        then:
        1 * defaultGenerator.generate({ it.dsl == GROOVY && it.testFramework == SPOCK })

        where:
        validPackageName << [
            'including.underscores_is.okay',
            'any._leading.underscore.is.valid',
            'numb3rs.are.okay123',
            'and.$so.are.dollars$'
        ]
    }
}
