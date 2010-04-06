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

package org.gradle.integtests

import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.Rule

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesExcludesAndClassifiersIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    private final GradleExecuter executer = dist.executer

    @Test
    public void checkExcludeAndClassifier() {
        File projectDir = new File(dist.samplesDir, "/userguide/artifacts/excludesAndClassifiers")
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
