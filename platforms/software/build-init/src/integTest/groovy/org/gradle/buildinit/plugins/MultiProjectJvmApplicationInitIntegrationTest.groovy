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

package org.gradle.buildinit.plugins

import groovy.io.FileType
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.buildinit.plugins.internal.modifiers.Language.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.Language.JAVA
import static org.gradle.buildinit.plugins.internal.modifiers.Language.KOTLIN
import static org.gradle.buildinit.plugins.internal.modifiers.Language.SCALA
import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.containsText
import static org.hamcrest.core.AllOf.allOf

abstract class AbstractMultiProjectJvmApplicationInitIntegrationTest extends AbstractJvmLibraryInitIntegrationSpec {

    abstract BuildInitDsl getBuildDsl()

    abstract Language getJvmLanguage()

    @Override
    String subprojectName() {
        return null
    }

    def "creates multi-project application sample when incubating flag = #incubating"() {
        given:
        def dsl = buildDsl
        def language = jvmLanguage.name
        def ext = jvmLanguage.extension
        def settingsFile = dsl.fileNameFor('settings')
        def buildFile = dsl.fileNameFor('build')

        when:
        def tasks = ['init', '--type', "${language}-application".toString(), '--split-project', '--dsl', dsl.id] + (incubating ? ['--incubating'] : [])
        run(tasks)

        then:
        targetDir.file(settingsFile).exists()
        !targetDir.file(buildFile).exists()

        def buildLogicDir = targetDir.file(incubating ? "build-logic" : "buildSrc")
        assertBuildLogicSources(dsl, language, buildLogicDir, settingsFile, buildFile)

        assertApplicationProjectsSources(buildFile, language, "org.example.", ext)

        when:
        succeeds "build"

        then:
        assertTestPassed("app", "org.example.app.MessageUtilsTest", "testGetMessage")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testConstructor")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testAdd")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testRemove")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testRemoveMissing")

        when:
        succeeds "run"

        then:
        outputContains("Hello World!")

        where:
        incubating << [true, false]
    }

    def "creates multi-project application with source package #description"() {
        def expectedPackagePrefix = expectedPackage.isEmpty() ? "" : "$expectedPackage."

        given:
        def dsl = buildDsl
        def language = jvmLanguage.name
        def ext = jvmLanguage.extension
        def settingsFile = dsl.fileNameFor('settings')
        def buildFile = dsl.fileNameFor('build')

        def sourcePackageOption = optionPackage == null ? [] : ['--package', optionPackage]
        def sourcePackageProperty = propertyPackage == null ? [] : ['-Porg.gradle.buildinit.source.package=' + propertyPackage]

        when:
        def tasks = ['init', '--type', "${language}-application".toString(), '--split-project', '--dsl', dsl.id] + sourcePackageProperty + sourcePackageOption
        run(tasks)

        then:
        targetDir.file(settingsFile).exists()
        !targetDir.file(buildFile).exists()

        def buildLogicDir = targetDir.file("buildSrc")
        assertBuildLogicSources(dsl, language, buildLogicDir, settingsFile, buildFile)

        assertApplicationProjectsSources(buildFile, language, expectedPackagePrefix, ext)

        expect:
        succeeds "build"

        when:
        succeeds "run"

        then:
        outputContains("Hello World!")

        where:
        description                             | optionPackage      | propertyPackage    | expectedPackage
        "default value"                         | null               | null               | "org.example"
        "from option"                           | "my.sourcepackage" | null               | "my.sourcepackage"
        "from property"                         | null               | "my.sourcepackage" | "my.sourcepackage"
        "from option when property is also set" | "my.sourcepackage" | "my.overridden"    | "my.sourcepackage"
        "from property with empty value"        | null               | ""                 | ""
    }

    def "creates multi-project application sample without comments configured via #description"() {
        given:
        def dsl = buildDsl
        def language = jvmLanguage.name
        def settingsFile = dsl.fileNameFor('settings')
        def buildFile = dsl.fileNameFor('build')

        def commentsOption = option == null ? [] : [option ? '--comments' : '--no-comments']
        def commentsProperty = property == null ? [] : ['-Porg.gradle.buildinit.comments=' + property]

        when:
        run([
            'init', '--use-defaults', '--dsl', dsl.id,
            '--type', language + '-application',
            '--split-project'
        ] + commentsOption + commentsProperty)

        then:
        targetDir.file(settingsFile).exists()
        !targetDir.file(buildFile).exists()

        def allFiles = getAllFiles(targetDir)
            .findAll { it.name !in ["gradle-wrapper.jar", "gradlew", "gradlew.bat"] }

        allFiles.each {
            assert !it.text.containsIgnoreCase("generated by")
        }

        when:
        succeeds "build"

        then:
        assertTestPassed("app", "org.example.app.MessageUtilsTest", "testGetMessage")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testConstructor")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testAdd")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testRemove")
        assertTestPassed("list", "org.example.list.LinkedListTest", "testRemoveMissing")

        when:
        succeeds "run"

        then:
        outputContains("Hello World!")

        where:
        description            | option | property
        "option"               | false  | null
        "property"             | null   | false
        "option over property" | false  | true
    }

    def getAllFiles(File dir) {
        List<File> files = []
        dir.eachFileRecurse(FileType.FILES) { file ->
            files << file
        }
        return files
    }

    void assertBuildLogicSources(BuildInitDsl dsl, String language, TestFile buildLogicDir, String settingsFile, String buildFile) {
        def commonConventionsPath = "src/main/${dsl.id}/buildlogic.${dsl.fileNameFor("${language}-common-conventions")}"

        buildLogicDir.assertHasDescendants(
            settingsFile,
            buildFile,
            commonConventionsPath,
            "src/main/${dsl.id}/buildlogic.${dsl.fileNameFor("${language}-application-conventions")}",
            "src/main/${dsl.id}/buildlogic.${dsl.fileNameFor("${language}-library-conventions")}",
        )

        buildLogicDir.file(commonConventionsPath).assertContents(
            containsText("JavaLanguageVersion.of(21)")
        )
    }

    void assertApplicationProjectsSources(String buildFile, String language, String expectedPackagePrefix, String ext) {
        def expectedPackageDirPrefix = packageToDir(expectedPackagePrefix)
        def appFiles = [buildFile,
                        "src/main/${language}/${expectedPackageDirPrefix}app/App.${ext}",
                        "src/main/${language}/${expectedPackageDirPrefix}app/MessageUtils.${ext}",
                        "src/test/${language}/${expectedPackageDirPrefix}app/MessageUtilsTest.${ext}",
                        "src/main/resources",
                        "src/test/resources"]
        targetDir.file("app").assertHasDescendants(appFiles*.toString())

        targetDir.file("app/$buildFile").assertContents(
            containsLine(allOf( // Ignore quote variations, `=` vs `set` and AppKt
                containsText("mainClass"),
                containsText("${expectedPackagePrefix}app.App")
            ))
        )

        def listFiles = [buildFile,
                         "src/main/${language}/${expectedPackageDirPrefix}list/LinkedList.${ext}",
                         "src/test/${language}/${expectedPackageDirPrefix}list/LinkedListTest.${ext}",
                         "src/main/resources",
                         "src/test/resources"]
        targetDir.file("list").assertHasDescendants(listFiles*.toString())

        def utilFiles = [
            buildFile,
            "src/main/${language}/${expectedPackageDirPrefix}utilities/JoinUtils.${ext}",
            "src/main/${language}/${expectedPackageDirPrefix}utilities/SplitUtils.${ext}",
            "src/main/${language}/${expectedPackageDirPrefix}utilities/StringUtils.${ext}",
            "src/main/resources",
            "src/test/resources"
        ]*.toString()
        targetDir.file("utilities").assertHasDescendants(utilFiles)
    }

    void assertTestPassed(String subprojectName, String className, String name) {
        def result = new DefaultTestExecutionResult(targetDir.file(subprojectName))
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed(name)
    }

    String packageToDir(String packageName) {
        packageName.replace('.', '/')
    }
}

class GroovyDslMultiProjectJavaApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.GROOVY
    }

    @Override
    Language getJvmLanguage() {
        return JAVA
    }
}

class GroovyDslMultiProjectGroovyApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.GROOVY
    }

    @Override
    Language getJvmLanguage() {
        return GROOVY
    }
}

class GroovyDslMultiProjectKotlinApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.GROOVY
    }

    @Override
    Language getJvmLanguage() {
        return KOTLIN
    }
}

class GroovyDslMultiProjectScalaApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.GROOVY
    }

    @Override
    Language getJvmLanguage() {
        return SCALA
    }
}


class KotlinDslMultiProjectJavaApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.KOTLIN
    }

    @Override
    Language getJvmLanguage() {
        return JAVA
    }
}

class KotlinDslMultiProjectGroovyApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.KOTLIN
    }

    @Override
    Language getJvmLanguage() {
        return GROOVY
    }
}

class KotlinDslMultiProjectKotlinApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.KOTLIN
    }

    @Override
    Language getJvmLanguage() {
        return KOTLIN
    }
}

class KotlinDslMultiProjectScalaApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.KOTLIN
    }

    @Override
    Language getJvmLanguage() {
        return SCALA
    }
}
