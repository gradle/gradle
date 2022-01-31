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

class SamplesWebQuickstartIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final Sample sample = new Sample(temporaryFolder, 'webApplication/quickstart/groovy')
    @Rule ReleasingPortAllocator portFinder = new ReleasingPortAllocator()

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "can build a war"() {
        given:
        super.sample sample

        when:
        executer.withTasks('clean', 'build').run()

        then:
        // Check contents of War
        TestFile warContents = file('war-tmp')
        sample.dir.file("build/libs/quickstart.war").unzipTo(warContents)
        warContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'index.jsp',
                'WEB-INF/classes/org/gradle/sample/Greeter.class',
                'WEB-INF/classes/greeting.txt',
                'WEB-INF/lib/log4j-1.2.15.jar',
                'WEB-INF/lib/commons-io-1.4.jar',
        )
    }
}
