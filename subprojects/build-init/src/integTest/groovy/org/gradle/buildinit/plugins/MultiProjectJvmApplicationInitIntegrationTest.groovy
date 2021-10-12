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

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import spock.lang.Unroll

import static org.gradle.buildinit.plugins.internal.modifiers.Language.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.Language.JAVA
import static org.gradle.buildinit.plugins.internal.modifiers.Language.KOTLIN
import static org.gradle.buildinit.plugins.internal.modifiers.Language.SCALA

abstract class AbstractMultiProjectJvmApplicationInitIntegrationTest extends AbstractInitIntegrationSpec {
    abstract BuildInitDsl getBuildDsl()

    @Override
    String subprojectName() {
        return null
    }

    @Unroll("creates multi-project application sample for #jvmLanguage with #scriptDsl build scripts, when incubating flag = #incubating")
    def "creates multi-project application sample"() {
        given:
        def dsl = scriptDsl as BuildInitDsl
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

        targetDir.file("buildSrc").assertHasDescendants(
            buildFile,
            "src/main/${dsl.id}/some.thing.${dsl.fileNameFor("${language}-common-conventions")}",
            "src/main/${dsl.id}/some.thing.${dsl.fileNameFor("${language}-application-conventions")}",
            "src/main/${dsl.id}/some.thing.${dsl.fileNameFor("${language}-library-conventions")}",
        )

        def appFiles = [buildFile,
            "src/main/${language}/some/thing/app/App.${ext}",
            "src/main/${language}/some/thing/app/MessageUtils.${ext}",
            "src/test/${language}/some/thing/app/MessageUtilsTest.${ext}",
            "src/main/resources",
            "src/test/resources"]
        targetDir.file("app").assertHasDescendants(appFiles*.toString())

        def listFiles = [buildFile,
            "src/main/${language}/some/thing/list/LinkedList.${ext}",
            "src/test/${language}/some/thing/list/LinkedListTest.${ext}",
            "src/main/resources",
            "src/test/resources"]
        targetDir.file("list").assertHasDescendants(listFiles*.toString())

        def utilFiles = [
            buildFile,
            "src/main/${language}/some/thing/utilities/JoinUtils.${ext}",
            "src/main/${language}/some/thing/utilities/SplitUtils.${ext}",
            "src/main/${language}/some/thing/utilities/StringUtils.${ext}",
            "src/main/resources",
            "src/test/resources"
        ]*.toString()
        targetDir.file("utilities").assertHasDescendants(utilFiles)

        when:
        succeeds "build"

        then:
        assertTestPassed("app", "some.thing.app.MessageUtilsTest", "testGetMessage")
        assertTestPassed("list", "some.thing.list.LinkedListTest", "testConstructor")
        assertTestPassed("list", "some.thing.list.LinkedListTest", "testAdd")
        assertTestPassed("list", "some.thing.list.LinkedListTest", "testRemove")
        assertTestPassed("list", "some.thing.list.LinkedListTest", "testRemoveMissing")

        when:
        succeeds "run"

        then:
        outputContains("Hello World!")

        where:
        [jvmLanguage, scriptDsl, incubating] << [
                [JAVA, GROOVY, KOTLIN, SCALA],
                [getBuildDsl()],
                [true, false]
        ].combinations()
    }

    def "can explicitly configure application not to split projects with #scriptDsl build scripts"() {
        given:
        def dsl = scriptDsl as BuildInitDsl

        expect:
        succeeds('init', '--type', "java-application", '--dsl', dsl.id)

        where:
        scriptDsl << getBuildDsl()
    }

    void assertTestPassed(String subprojectName, String className, String name) {
        def result = new DefaultTestExecutionResult(targetDir.file(subprojectName))
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed(name)
    }
}

class GroovyDslMultiProjectJvmApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.GROOVY
    }
}

class KotlinDslMultiProjectJvmApplicationInitIntegrationTest extends AbstractMultiProjectJvmApplicationInitIntegrationTest {
    @Override
    BuildInitDsl getBuildDsl() {
        return BuildInitDsl.KOTLIN
    }
}
