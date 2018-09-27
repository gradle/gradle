/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SamplesCustomBuildLanguageIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'customBuildLanguage')

    @Before
    void setup() {
        executer.withRepositoryMirrors()
    }

    @Test
    void testBuildProductDistributions() {
        TestFile rootDir = sample.dir
        executer.inDirectory(rootDir).withTasks('clean', 'dist').run()

        TestFile expandDir = file('expand-basic')
        rootDir.file('basicEdition/build/distributions/some-company-basic-edition-1.0.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'readme.txt',
                'end-user-license-agreement.txt',
                'bin/start.sh',
                'lib/some-company-identity-management-1.0.jar',
                'lib/some-company-billing-1.0.jar',
                'lib/commons-lang-2.4.jar'
        )

        expandDir = file('expand-enterprise')
        rootDir.file('enterpriseEdition/build/distributions/some-company-enterprise-edition-1.0.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'readme.txt',
                'end-user-license-agreement.txt',
                'bin/start.sh',
                'lib/some-company-identity-management-1.0.jar',
                'lib/some-company-billing-1.0.jar',
                'lib/some-company-reporting-1.0.jar',
                'lib/commons-lang-2.4.jar',
                'lib/commons-io-1.2.jar'
        )
    }
}
