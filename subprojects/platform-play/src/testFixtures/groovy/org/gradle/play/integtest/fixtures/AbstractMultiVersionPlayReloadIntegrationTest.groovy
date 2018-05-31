/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.integtest.fixtures

import org.gradle.integtests.fixtures.TargetCoverage

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

@TargetCoverage({ PlayCoverage.DEFAULT })
abstract class AbstractMultiVersionPlayReloadIntegrationTest extends AbstractMultiVersionPlayContinuousBuildIntegrationTest {
    protected boolean serverRestart() {
        poll {
            assert serverStartCount > 1
        }
        return true
    }

    protected boolean serverStarted() {
        poll {
            assert serverStartCount == 1
        }
        return true
    }

    protected noServerRestart() {
        if (!versionNumber.toString().startsWith("2.2")) {
            assert serverStartCount == 1
        }
    }

    protected getServerStartCount() {
        // play - Application started
        // Play - Application started
        gradle.standardOutput.count('lay - Application started')
    }
}
