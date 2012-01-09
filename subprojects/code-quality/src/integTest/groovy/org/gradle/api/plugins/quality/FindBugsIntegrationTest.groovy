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
package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.hamcrest.Matcher

import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.containsLine

class FindBugsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        writeBuildFile()
    }
    
    def "analyze empty project"() {
        expect:
        succeeds('check')
    }
    
    def "analyze good code"() {
        file("src/main/java/org/gradle/Class1.java") << "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") << "package org.gradle; class Class1Test { public boolean isFoo(Object arg) { return true; } }"
        
        expect:
        succeeds("check")
		file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))
		file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }
    
    void "analyze bad code"() {
        file("src/main/java/org/gradle/Class1.java") << "package org.gradle; class Class1 { public boolean equals(Object arg) { return true; } }"

        expect:
        fails("check")
		failure.assertHasDescription("Execution failed for task ':findbugsMain'")
        failure.assertThatCause(startsWith("FindBugs reported warnings."))
		file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))
    }

    private Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }
  
    private void writeBuildFile() {
        file("build.gradle") << """
        apply plugin: "java"
        apply plugin: "findbugs"
        
        repositories {
            mavenCentral()
        }
        """
    }
}
