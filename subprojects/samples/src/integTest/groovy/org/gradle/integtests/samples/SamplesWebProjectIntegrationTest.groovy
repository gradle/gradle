/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule

class SamplesWebProjectIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample sample = new Sample(temporaryFolder, 'webApplication/customized/groovy')
    @Rule ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "can build war"() {
        when:
        super.sample sample
        succeeds('clean', 'assemble')

        then:
        TestFile tmpDir = file('unjar')
        sample.dir.file("build/libs/customized-1.0.war").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'root.txt',
                'META-INF/MANIFEST.MF',
                'WEB-INF/classes/org/gradle/HelloServlet.class',
                'WEB-INF/additional.xml',
                'WEB-INF/webapp.xml',
                'WEB-INF/web.xml',
                'webapp.html')
    }
}
