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

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.buildinit.plugins.internal.modifiers.Language.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.Language.JAVA
import static org.gradle.buildinit.plugins.internal.modifiers.Language.KOTLIN
import static org.gradle.buildinit.plugins.internal.modifiers.Language.SCALA

class MultiProjectJvmApplicationInitIntegrationTest extends AbstractIntegrationSpec {
    final def targetDir = testDirectory.createDir("some-thing")

    def setup() {
        executer.withRepositoryMirrors()
        executer.beforeExecute {
            executer.inDirectory(targetDir)
            executer.ignoreMissingSettingsFile()
        }
    }

    @Ignore("wip: remove once a new `kotlin-dsl` version is published")
    @Unroll("creates multi-project application sample for #jvmLanguage with #scriptDsl build scripts")
    @ToBeFixedForConfigurationCache(iterationMatchers = ".*Kotlin build scripts", because = "Kotlin Gradle Plugin")
    def "creates multi-project application sample"() {
        given:
        def dsl = scriptDsl as BuildInitDsl
        def language = jvmLanguage.name
        def ext = jvmLanguage.extension
        def settingsFile = dsl.fileNameFor('settings')
        def buildFile = dsl.fileNameFor('build')

        when:
        run('init', '--type', "${language}-application", '--split-project', '--dsl', dsl.id)

        then:
        targetDir.file(settingsFile).exists()
        !targetDir.file(buildFile).exists()

        targetDir.file("buildSrc").assertHasDescendants(
            buildFile,
            "src/main/${dsl.id}/some.thing.${dsl.fileNameFor("${language}-common-conventions")}",
            "src/main/${dsl.id}/some.thing.${dsl.fileNameFor("${language}-application-conventions")}",
            "src/main/${dsl.id}/some.thing.${dsl.fileNameFor("${language}-library-conventions")}",
        )

        targetDir.file("app").assertHasDescendants(
            buildFile,
            "src/main/${language}/some/thing/app/App.${ext}",
            "src/main/${language}/some/thing/app/MessageUtils.${ext}",
            "src/test/${language}/some/thing/app/MessageUtilsTest.${ext}",
            "src/main/resources",
            "src/test/resources"
        )
        targetDir.file("list").assertHasDescendants(
            buildFile,
            "src/main/${language}/some/thing/list/LinkedList.${ext}",
            "src/test/${language}/some/thing/list/LinkedListTest.${ext}",
            "src/main/resources",
            "src/test/resources"
        )

        targetDir.file("utilities").assertHasDescendants(
            buildFile,
            "src/main/${language}/some/thing/utilities/JoinUtils.${ext}",
            "src/main/${language}/some/thing/utilities/SplitUtils.${ext}",
            "src/main/${language}/some/thing/utilities/StringUtils.${ext}",
            "src/main/resources",
            "src/test/resources"
        )

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
        [jvmLanguage, scriptDsl] << [[JAVA, GROOVY, KOTLIN, SCALA], ScriptDslFixture.SCRIPT_DSLS].combinations()
    }

    def "can explicitly configure application not to split projects"() {
        expect:
        succeeds('init', '--type', "java-application", '--dsl', 'groovy')
    }

    void assertTestPassed(String subprojectName, String className, String name) {
        def result = new DefaultTestExecutionResult(targetDir.file(subprojectName))
        result.assertTestClassesExecuted(className)
        result.testClass(className).assertTestPassed(name)
    }

}
