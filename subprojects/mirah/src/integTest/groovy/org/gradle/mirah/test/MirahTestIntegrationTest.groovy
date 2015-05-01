/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.mirah.test

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.ForkMirahCompileInDaemonModeFixture
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class MirahTestIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)
    @Rule public final ForkMirahCompileInDaemonModeFixture forkMirahCompileInDaemonModeFixture = new ForkMirahCompileInDaemonModeFixture(executer, temporaryFolder)

    // FIXME: We do test, but we do not test using multiline descriptions.
    def executesTestsWithMultiLineDescriptions() {
        file("build.gradle") << """
apply plugin: 'mirah'

buildscript {
    repositories {
        mavenCentral()
    }
    
    dependencies {
        classpath 'org.mirah:mirah:0.1.4'
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testCompile "junit:junit:4.12"
}
        """

        when:
        file("src/test/mirah/MultiLineNameTest.mirah") << '''
package org.gradle

import org.junit.Test

class MultiLineSuite
    $Test
    def testNotSoManyLines:void
        org::junit::Assert.assertEquals(1, 1)
    end
end
        '''

        then:
        succeeds("test")

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("org.gradle.MultiLineSuite")
        result.testClass("org.gradle.MultiLineSuite").assertTestPassed("testNotSoManyLines")
    }
}
