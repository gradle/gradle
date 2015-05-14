/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

class MultiProjectContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def upstreamSource, downstreamSource

    def setup() {
        settingsFile << "include 'upstream', 'downstream'"
        buildFile << """
subprojects {
    apply plugin: 'java'
    repositories { mavenCentral() }
    dependencies {
        testCompile 'junit:junit:4.12'
    }
}
project(':downstream') {
    dependencies {
        compile project(":upstream")
    }
}
"""
        upstreamSource = file("upstream/src/main/java/upstream/A.java").createFile()
        upstreamSource << """
package upstream;

public class A { }
"""
        downstreamSource = file("downstream/src/main/java/downstream/B.java").createFile()
        downstreamSource << """
package downstream;
import upstream.A;

class B { }
"""
    }

    def "changes to upstream project triggers build of downstream"() {
        expect:
        succeeds("build")
        executedAndNotSkipped(":upstream:compileJava", ":upstream:build", ":downstream:compileJava", ":downstream:build")

        when: "change to upstream builds both up and down stream"
        upstreamSource << """
class Change {}
"""
        then:
        succeeds()
        executedAndNotSkipped(":upstream:compileJava", ":downstream:compileJava", ":upstream:build")

        when: "change to downstream doesn't build upstream"
        downstreamSource << """
class Change2 {}
"""
        then:
        succeeds()
        executedAndNotSkipped(":downstream:compileJava", ":downstream:build")
        skipped(":upstream:compileJava", ":upstream:build")

        when: "broken change to downstream fails the build"
        upstreamSource << "class BrokenChange { "
        then:
        fails()

        when: "fixing broken change builds up and down stream"
        upstreamSource << "} "
        then:
        succeeds()
        executedAndNotSkipped(":upstream:compileJava", ":upstream:build", ":downstream:compileJava")
    }

    def "Task can specify root directory of multi project build as a task input; changes are respected"() {
        given:
        buildFile << """
allprojects {
    task a {
        inputs.dir rootDir
        doLast {
        }
    }
}
"""
        expect: "executing all tasks"
        succeeds("a")
        executedAndNotSkipped(":a", ":upstream:a", ":downstream:a")
        when:
        file("A").text = "A"
        then:
        succeeds()
        executedAndNotSkipped(":a", ":upstream:a", ":downstream:a")

        expect: "executing a subproject task"
        succeeds(":downstream:a")
        executedAndNotSkipped(":downstream:a")
        notExecuted(":a", ":upstream:a")
        when:
        file("B").text = "B"
        then:
        succeeds()
        executedAndNotSkipped(":downstream:a")

        expect: "executing two tasks"
        succeeds(":upstream:a", ":a")
        executedAndNotSkipped(":upstream:a", ":a")
        notExecuted(":downstream:a")
        when:
        file("C").text = "C"
        then:
        succeeds()
        executedAndNotSkipped(":upstream:a", ":a")
        notExecuted(":downstream:a")

    }

    def "reasonable sized multi-project with --parallel"() {
        given:
        executer.withArgument("--parallel")
        buildTimeout = buildTimeout * 2
        def numberOfProjects = 25
        def numberOfSources = 10
        (0..numberOfProjects).each { idx ->
            def projectName = "project$idx"
            settingsFile << """
include '$projectName'
"""
            (0..numberOfSources).each { sourceIdx ->
                file("${projectName}/src/main/java/${projectName}/A${sourceIdx}.java").createFile() << """
package ${projectName};
class A${sourceIdx} {}
"""
                file("${projectName}/src/test/java/${projectName}/A${sourceIdx}Test.java").createFile() << """
package ${projectName};

import org.junit.Test;

public class A${sourceIdx}Test {
   @Test public void testMethod() {
   }
}
"""
            }
        }
        buildFile << """
def generatedProjects() {
   subprojects.find { it.name.startsWith("project") }
}
configure(generatedProjects()) {
   dependencies {
       compile project(":upstream")
   }
}
"""
        expect:
        succeeds("build")

        when:
        downstreamSource << """
class Change {}
"""
        then:
        succeeds()

        when:
        upstreamSource << """
class Change2 {}
"""
        then:
        succeeds()
    }
}
