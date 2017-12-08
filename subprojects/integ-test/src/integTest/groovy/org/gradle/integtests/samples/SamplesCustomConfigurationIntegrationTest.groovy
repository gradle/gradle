/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.junit.Rule

class SamplesCustomConfigurationIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider, 'userguide/dependencies/declaringCustomConfigurations')

    def setup() {
        executer.requireGradleDistribution()
    }

    def "can use custom configuration"() {
        setup:
        executer.inDirectory(sample.dir)

        when:
        succeeds('preCompileJsps')

        then:
        sample.dir.file('build/compiled-jsps/org/apache/jsp/hello_jsp.java').isFile()
    }
}
