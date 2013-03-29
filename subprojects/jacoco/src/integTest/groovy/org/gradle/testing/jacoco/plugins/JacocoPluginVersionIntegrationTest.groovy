/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.junit.Test
import spock.lang.IgnoreIf

@IgnoreIf({ OperatingSystem.current().isWindows() || Jvm.current().isIbmJvm() })
@TargetVersions(['0.6.0.201210061924', '0.6.2.201302030002'])
class JacocoPluginVersionIntegrationTest extends MultiVersionIntegrationSpec {

    @Test
    public void canRunJacocoWithDifferentToolVersions() {
        given:
        buildFile << """
        apply plugin: "java"
        apply plugin: "jacoco"

        repositories {
            mavenCentral()
        }

        dependencies {
            testCompile 'junit:junit:4.10'
        }
        jacoco {
            toolVersion = '$org.gradle.integtests.fixtures.MultiVersionIntegrationSpec.version'
        }
        """
        createTestFiles();
        when:
        executer.withArgument("-i")
        succeeds('jacocoTestReport')
        then:
        file("build/reports/jacoco/test/html/index.html").exists()
    }

    private void createTestFiles() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; public class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; import org.junit.Test; public class Class1Test { @Test public void someTest() { new Class1().isFoo(\"test\"); } }"
    }
}
