/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.quality.codenarc

import org.gradle.test.fixtures.file.TestFile


trait CodeNarcTestFixture {
    def goodCode() {
        file("src/main/groovy/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/test/groovy/org/gradle/testclass1.java") << "package org.gradle; public class testclass1 { @org.junit.Test public void test1() { } }"
        file("src/main/groovy/org/gradle/Class2.groovy") << "package org.gradle; class Class2 { }"
        file("src/test/groovy/org/gradle/TestClass2.groovy") << "package org.gradle; class TestClass2 { }"
    }

    def badCode() {
        file("src/main/groovy/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/main/groovy/org/gradle/Class2.groovy") << "package org.gradle; class Class2 { }"
        file("src/test/groovy/org/gradle/TestClass1.java") << "package org.gradle; public class TestClass1 { @org.junit.Test public void test1() { } }"
        file("src/test/groovy/org/gradle/testclass2.groovy") << "package org.gradle; class testclass2 { }"
    }

    TestFile report(String sourceSet, String ext = 'html') {
        file(reportPath(sourceSet, ext))
    }

    TestFile report(File projectDir, String sourceSet, String ext = 'html') {
        new TestFile(projectDir, reportPath(sourceSet, ext))
    }

    private String reportPath(String sourceSet, String ext) {
        return "build/reports/codenarc/${sourceSet}.${ext}"
    }

    TestFile writeRuleFile() {
        file("config/codenarc/codenarc.xml") << """
            <ruleset xmlns="http://codenarc.org/ruleset/1.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
                    xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
                <ruleset-ref path="rulesets/naming.xml"/>
            </ruleset>
        """
    }
}
