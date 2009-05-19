/*
 * Copyright 2007 the original author or authors.
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

import org.junit.runner.RunWith
import org.junit.Test

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class WrapperProjectIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void wrapperSample() {
        String nl = System.properties['line.separator']
        File wrapperSampleDir = new File(dist.samplesDir, 'wrapper-project')

        executer.inDirectory(wrapperSampleDir).withTasks('wrapper').run()
        Map result = Executer.executeWrapper(dist.gradleHomeDir.absolutePath, wrapperSampleDir.absolutePath, ['hello'],
            [:], 'build.gradle', Executer.QUIET, false)
        String compareValue =  result.output.substring(result.output.size() - 'hello'.size() - nl.size())
        assert compareValue == 'hello' + nl
    }
}
