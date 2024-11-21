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
import org.gradle.internal.id.IdGenerator;

import java.time.Instant;

@NonNullApi
public class DefaultRootTestEventReporter extends DefaultGroupTestEventReporter {
    private String failureMessage;

    public DefaultRootTestEventReporter(TestResultProcessor processor, IdGenerator<?> idGenerator, TestDescriptorInternal testDescriptor) {
        super(processor, idGenerator, null, testDescriptor);
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        if (failureMessage != null) {
            throw new VerificationException(failureMessage);
        }
    }

    @Override
    public void failed(Instant endTime) {
        failureMessage = "Test(s) failed.";
        super.failed(endTime);
    }

    @Override
    public void failed(Instant endTime, String message) {
        failureMessage = message;
        super.failed(endTime, message);
    }
}
