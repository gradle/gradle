/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.test.preconditions

import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.TestPrecondition

public class TestKitPreconditions {

    public static class LowestMajorGradleIsAvailable implements TestPrecondition {
        @Override
        public boolean isSatisfied() throws Exception {
            def releasedGradleVersions = new ReleasedVersionDistributions();
            def probeVersions = ["4.10.3", "5.6.4", "6.9.2", "7.5.1", "7.6"]
            String compatibleVersion = probeVersions.find {version ->
                releasedGradleVersions.getDistribution(version)?.worksWith(Jvm.current())
            }
            return
        }
    }

}
