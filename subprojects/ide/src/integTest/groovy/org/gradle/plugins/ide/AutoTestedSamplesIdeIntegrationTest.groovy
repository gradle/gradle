/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide

import org.gradle.integtests.fixtures.AbstractAutoTestedSamplesTest
import org.junit.Test

class AutoTestedSamplesIdeIntegrationTest extends AbstractAutoTestedSamplesTest {

    @Test
    void runSamples() {
        runSamplesFrom("src/main")
    }

    @Override
    protected void beforeSample(File file, String tagSuffix) {
        if (tagSuffix == "WithSourceDirDeprecations") {
            executer.expectDeprecationWarning('The IdeaModule.testSourceDirs property has been deprecated. This is scheduled to be removed in Gradle 8.0. Please use the testSources property instead.')
            executer.expectDeprecationWarning('The IdeaModule.testResourceDirs property has been deprecated. This is scheduled to be removed in Gradle 8.0. Please use the testResources property instead.')
        }
    }
}
