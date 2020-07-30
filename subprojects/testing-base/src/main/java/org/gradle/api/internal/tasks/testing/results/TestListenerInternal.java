/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

@UsedByScanPlugin
@EventScope(Scopes.Build.class)
public interface TestListenerInternal {
    void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent);

    void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent);

    void output(TestDescriptorInternal testDescriptor, TestOutputEvent event);
}
