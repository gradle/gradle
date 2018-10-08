/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins.quality.findbugs

import groovy.xml.XmlUtil
import org.gradle.integtests.fixtures.AbstractProjectRelocationIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK8_OR_EARLIER)
class FindBugsRelocationIntegrationTest extends AbstractProjectRelocationIntegrationTest {

    void setup() {
        executer.beforeExecute {
            noDeprecationChecks()
        }
    }

    @Override
    protected String getTaskName() {
        return ":findbugs"
    }

    @Override
    protected void setupProjectIn(TestFile projectDir) {
        // Has DM_EXIT
        projectDir.file('src/main/java/org/gradle/BadClass.java') << 'package org.gradle; public class BadClass { public boolean isFoo(Object arg) { System.exit(1); return true; } }'

        projectDir.file("build.gradle") << """
            apply plugin: "findbugs"

            ${mavenCentralRepository()}

            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("build/classes")
                source "src/main/java"
                classpath = files()
            }

            task findbugs(type: FindBugs) {
                dependsOn compile
                classes = files("build/classes")
                classpath = files()
                ignoreFailures = true
            }
        """
    }

    @Override
    protected extractResultsFrom(TestFile projectDir) {
        def findbugsXml = new XmlSlurper().parse(projectDir.file("build/reports/findbugs/findbugs.xml"))

        // Remove Jar elements that have a full path to the jar being analyzed
        findbugsXml.Project.Jar.replaceNode {}
        // Remove ClassProfile elements that are sometimes different, but do not matter
        findbugsXml.FindBugsSummary.FindBugsProfile.replaceNode {}

        def summaryAttributes = findbugsXml.FindBugsSummary[0].attributes()
        summaryAttributes.timestamp = "0"
        summaryAttributes.cpu_seconds = "0"
        summaryAttributes.clock_seconds = "0"
        summaryAttributes.gc_seconds = "0"
        summaryAttributes.peak_mbytes = "0"
        summaryAttributes.alloc_mbytes = "0"

        def rootAttributes = findbugsXml.attributes()
        rootAttributes.analysisTimestamp = "0"
        rootAttributes.timestamp = "0"

        return XmlUtil.serialize(findbugsXml)
    }
}
