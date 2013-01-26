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
package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.junit.Test

@TargetVersions(['4.3', '5.0.0'])
class PmdPluginVersionIntegrationTest extends MultiVersionIntegrationSpec {

    @Test
    public void canRunPmdWithDifferentToolVersions() {
        given:
        buildFile << """
        apply plugin: "java"
        apply plugin: "pmd"

        repositories {
            mavenCentral()
        }
        pmd {
            toolVersion = '$version'
            ignoreFailures = true
        }"""
        createTestFiles();
        when:
        succeeds('pmdMain', 'pmdTest')
        then:
        output.contains("2 PMD rule violations were found. See the report at:")
    }

    private void createTestFiles() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; class Class1Test { {} public boolean equals(Object arg) { return true; } }"
    }
}
