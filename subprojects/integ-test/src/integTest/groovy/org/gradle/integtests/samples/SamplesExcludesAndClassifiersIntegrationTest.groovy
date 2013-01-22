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
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not
import static org.junit.Assert.assertThat

/**
 * @author Hans Dockter
 */
class SamplesExcludesAndClassifiersIntegrationTest extends AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'userguide/artifacts/excludesAndClassifiers')

    @Test
    public void checkExcludeAndClassifier() {
        File projectDir = sample.dir
        String outputCompile = executer.inDirectory(projectDir).withTasks('clean', 'resolveCompile').run().getOutput()
        String outputRuntime = executer.inDirectory(projectDir).withTasks('clean', 'resolveRuntime').run().getOutput()
        assertThat(outputCompile, not(containsString("commons")))
        assertThat(outputRuntime, not(containsString("commons")))
        assertThat(outputCompile, not(containsString("reports")))
        assertThat(outputRuntime, not(containsString("reports")))
        assertThat(outputCompile, not(containsString("shared")))
        assertThat(outputRuntime, containsString("shared"))

        assertThat(outputCompile, containsString("service-1.0-jdk15"))
        assertThat(outputCompile, containsString("service-1.0-jdk14"))
    }

}
