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

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.*

class CheckstyleIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        // without this, running from IDE gives ANTLR class loader conflict
        executer.withForkingExecuter()

        writeBuildFile()
        writeConfigFile()
    }
    
    def "analyze empty project"() {
        expect:
        succeeds('check')
    }

    def "analyze good code"() {
        file('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        file('src/test/java/org/gradle/TestClass1.java') << 'package org.gradle; class TestClass1 { }'
        file('src/main/groovy/org/gradle/Class2.java') << 'package org.gradle; class Class1 { }'
        file('src/test/groovy/org/gradle/TestClass2.java') << 'package org.gradle; class TestClass1 { }'

        expect:
        succeeds('check')
        file("build/reports/checkstyle/main.xml").text.contains("org/gradle/Class1")
        file("build/reports/checkstyle/main.xml").text.contains("org/gradle/Class2")
        file("build/reports/checkstyle/test.xml").text.contains("org/gradle/TestClass1")
        file("build/reports/checkstyle/test.xml").text.contains("org/gradle/TestClass2")
    }

    private Matcher<String> containsClass(String className) {
        return containsLine(containsString(className.replace(".", File.separator) + ".java"))
    }

    def "analyze bad code"() {
        file("src/main/java/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/test/java/org/gradle/testclass1.java") << "package org.gradle; class testclass1 { }"
        file("src/main/groovy/org/gradle/class2.java") << "package org.gradle; class class2 { }"
        file("src/test/groovy/org/gradle/testclass2.java") << "package org.gradle; class testclass2 { }"

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'")
        failure.assertThatCause(startsWith("Checkstyle rule violations were found. See the report at"))
        file("build/reports/checkstyle/main.xml").text.contains("org/gradle/class1")
        file("build/reports/checkstyle/main.xml").text.contains("org/gradle/class2")
    }

    private void writeBuildFile() {
        file("build.gradle") << """
apply plugin: "groovy"
apply plugin: "checkstyle"

repositories {
    mavenCentral()
}

dependencies {
    groovy localGroovy()
}
        """
    }

    private void writeConfigFile() {
        file("config/checkstyle/checkstyle.xml") << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
        """
    }
}
