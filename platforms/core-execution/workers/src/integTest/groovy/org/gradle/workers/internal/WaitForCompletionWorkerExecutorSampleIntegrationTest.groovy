/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.util.internal.TextUtil

@IntegrationTestTimeout(60)
class WaitForCompletionWorkerExecutorSampleIntegrationTest extends AbstractWorkerExecutorSampleIntegrationTest {
    String getSampleName() {
        "workerApi/waitForCompletion"
    }

    @Override
    void assertSampleSpecificOutcome(String dsl) {
        assert TextUtil.normaliseFileSeparators(result.output).contains("Created ${workerExecutorSample(dsl).file("sources").allDescendants().size()} reversed files in build/reversed")
    }
}
