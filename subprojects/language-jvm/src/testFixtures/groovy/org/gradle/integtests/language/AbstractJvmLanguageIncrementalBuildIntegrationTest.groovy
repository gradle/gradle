/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.language

import org.apache.commons.lang.StringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.jvm.JvmSourceFile
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractJvmLanguageIncrementalBuildIntegrationTest extends AbstractIntegrationSpec {
    abstract TestJvmComponent getTestComponent();

    List<TestFile> sourceFiles
    List<TestFile> resourceFiles

    String mainCompileTaskName
    def setup() {

        mainCompileTaskName = ":compileMainJarMain${StringUtils.capitalize(getTestComponent().languageName)}"
        sourceFiles = testComponent.writeSources(file("src/main"))
        resourceFiles = testComponent.writeResources(file("src/main/resources"))

        buildFile << """
    plugins {
        id 'jvm-component'
        id '${testComponent.languageName}-lang'
    }

    ${mavenCentralRepository()}

    model {
        components {
            main(JvmLibrarySpec)
        }
    }
"""
    }

    def expectDeprecationWarnings() {
        executer.expectDocumentedDeprecationWarning("The ${testComponent.languageName}-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    @ToBeFixedForInstantExecution(bottomSpecs = "JavaLanguageIncrementalBuildIntegrationTest")
    def "builds jar"() {
        when:
        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped mainCompileTaskName, ":processMainJarMainResources", ":createMainJar", ":mainJar"

        and:
        jarFile("build/jars/main/jar/main.jar").hasDescendants(testComponent.expectedOutputs*.fullPath as String[])
    }

    @ToBeFixedForInstantExecution
    def "does not re-execute build with no change"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        expectDeprecationWarnings()
        run "mainJar"

        then:
        allSkipped()
    }

    @ToBeFixedForInstantExecution
    def "rebuilds jar and classfile is removed when source file removed"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        sourceFiles[1].delete()
        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped ":createMainJar", ":mainJar"


        and:
        assertOutputs([testComponent.sources[0].classFile], [testComponent.resources[0], testComponent.resources[1]])
    }

    @ToBeFixedForInstantExecution
    def "rebuilds jar without resource when resource removed"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        resourceFiles[1].delete()
        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped ":processMainJarMainResources", ":createMainJar", ":mainJar"

        and:
        assertOutputs([testComponent.sources[0].classFile, testComponent.sources[1].classFile], [testComponent.resources[0]])
    }

    @ToBeFixedForInstantExecution
    def "rebuilds jar when source file changed"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        testComponent.changeSources(sourceFiles)
        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped mainCompileTaskName, ":createMainJar", ":mainJar"
    }

    @ToBeFixedForInstantExecution
    def "rebuilds jar when resource file changed"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        resourceFiles[0].text = "Some different text"
        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped ":processMainJarMainResources", ":createMainJar", ":mainJar"
    }

    @ToBeFixedForInstantExecution
    def "rebuilds jar when source file added"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        testComponent.writeAdditionalSources(file("src/main"))

        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped mainCompileTaskName, ":createMainJar", ":mainJar"

        and:
        file("build/classes/main/jar/Extra.class").assertExists()
        jarFile("build/jars/main/jar/main.jar").assertContainsFile("Extra.class")
    }

    @ToBeFixedForInstantExecution
    def "rebuilds jar when resource file added"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        file("src/main/resources/Extra.txt") << "an extra resource"
        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped ":processMainJarMainResources", ":createMainJar", ":mainJar"

        and:
        file("build/resources/main/jar/Extra.txt").assertExists()
        jarFile("build/jars/main/jar/main.jar").assertContainsFile("Extra.txt")
    }

    @ToBeFixedForInstantExecution
    def "recompiles but does not rebuild jar when source file changed such that bytecode is the same"() {
        given:
        expectDeprecationWarnings()
        run "mainJar"

        when:
        sourceFiles[0].text = sourceFiles[0].text + "// Line trailing comment"
        expectDeprecationWarnings()
        run "mainJar"

        then:
        executedAndNotSkipped mainCompileTaskName
        skipped ":createMainJar", ":mainJar"
    }

    def assertOutputs(List<JvmSourceFile> expectedClasses, List<JvmSourceFile> expectedResources) {
        String[] classes = expectedClasses.collect { it.fullPath }
        String[] resources = expectedResources.collect { it.fullPath }
        file("build/classes/main/jar").assertHasDescendants(classes)
        file("build/resources/main/jar").assertHasDescendants(resources)
        jarFile("build/jars/main/jar/main.jar").hasDescendants(classes + resources as String[])
        return true
    }

    private JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }
}
