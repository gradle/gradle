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
package org.gradle.testkit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Rule

@LeaksFileHandles
class TestKitSamplesIntegrationTest extends AbstractIntegrationSpec {

    @Rule Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        executer.requireGradleHome()
    }

    @UsesSample("testKit/testKitJunit")
    def junit() {
        expect:
        executer.inDirectory(sample.dir)
        succeeds "check"
    }

    @UsesSample("testKit/testKitSpock")
    def spock() {
        expect:
        executer.inDirectory(sample.dir)
        succeeds "check"
    }

    @UsesSample("testKit/testKitSpockClasspath")
    def buildscriptClasspath() {
        expect:
        executer.inDirectory(sample.dir)
        succeeds "check"
    }
}
