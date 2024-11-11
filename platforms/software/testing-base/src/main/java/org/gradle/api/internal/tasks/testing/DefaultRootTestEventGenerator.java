/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.VerificationException;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;

import javax.annotation.Nullable;
import java.time.Instant;

@NonNullApi
public class DefaultRootTestEventGenerator extends DefaultCompositeTestEventGenerator {
    private boolean failed;

    public DefaultRootTestEventGenerator(TestResultProcessor processor, IdGenerator<?> idGenerator, @Nullable TestDescriptorInternal parent, TestDescriptorInternal testDescriptor) {
        super(processor, idGenerator, parent, testDescriptor);
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        if (failed) {
            throw new VerificationException("Test(s) failed.");
        }
    }

    @Override
    public void completed(Instant endTime, TestResult.ResultType resultType) {
        if (resultType == TestResult.ResultType.FAILURE) {
            failed = true;
        }
        super.completed(endTime, resultType);
    }
}
