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
class WrapperProject {
    // Injected by test runner
    private GradleDistribution dist;

    @Test
    public void wrapperSample() {
        String nl = System.properties['line.separator']
        File waterDir = new File(dist.samplesDir, 'wrapper-project')

        Executer.execute(dist.gradleHomeDir.absolutePath, waterDir.absolutePath, ['wrapper'])
        Map result = Executer.executeWrapper(dist.gradleHomeDir.absolutePath, waterDir.absolutePath, ['hello'])
        String compareValue =  result.output.substring(result.output.size() - 'hello'.size() - nl.size())
        assert compareValue == 'hello' + nl
    }
}
