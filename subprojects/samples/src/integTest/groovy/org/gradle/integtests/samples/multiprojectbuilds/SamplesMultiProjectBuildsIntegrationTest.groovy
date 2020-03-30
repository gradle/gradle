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

package org.gradle.integtests.samples.multiprojectbuilds

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import spock.lang.Unroll

class SamplesMultiProjectBuildsIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @Unroll
    @UsesSample("userguide/multiproject/dependencies/outgoingArtifact")
    def "can produce outgoing artifact and depend on it from other project with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('build')

        then:
        dslDir.file('producer/build/generated-resources/build-info.properties').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }
}
