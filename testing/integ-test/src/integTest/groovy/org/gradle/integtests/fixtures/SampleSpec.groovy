/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import spock.lang.Specification

class SampleSpec extends Specification {

    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    Sample sample = new Sample(testDirectoryProvider, 'java/multiproject')
    @Rule TestRule rule = RuleChain.outerRule(testDirectoryProvider).around(sample)

    def 'sample dir'() {
        // Sample.dir is named after sample, test method and test class
        expect:
        sample.dir.name == 'multiproject'
        sample.dir.parentFile.parentFile.name == 'sample_dir'
        sample.dir.parentFile.parentFile.parentFile.name == 'SampleSpec'
    }
}
