/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test

class EclipseIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void canCreateAndDeleteMetaData() {
        File buildFile = testFile("master/build.gradle")
        usingBuildFile(buildFile).run()
    }

    @Test
    void sourceEntriesInClasspathFileAreSortedAsPerUsualConvention() {
        def expectedOrder = [
            "src/main/java",
            "src/main/groovy",
            "src/main/resources",
            "src/test/java",
            "src/test/groovy",
            "src/test/resources",
            "src/integTest/java",
            "src/integTest/groovy",
            "src/integTest/resources"
        ]

        expectedOrder.each { testFile(it).mkdirs() }

        def buildScript = """
apply plugin: "java"
apply plugin: "groovy"
apply plugin: "eclipse"

sourceSets {
    integTest {
        resources { srcDir "src/integTest/resources" }
        java { srcDir "src/integTest/java" }
        groovy { srcDir "src/integTest/groovy" }
    }
}
        """

        usingBuildScript(buildScript).withTasks("eclipse").run()

        def classpath = parseClasspathFile()
        def sourceEntries = classpath.classpathentry.findAll { it.@kind == "src" }

        assert sourceEntries*.@path == expectedOrder
    }

    private parseClasspathFile() {
        parseXmlFile(".classpath")
    }

    private parseProjectFile() {
        parseXmlFile(".project")
    }

    private parseXmlFile(filename) {
        def file = testFile(filename).assertExists()
        new XmlSlurper().parse(file)
    }
}