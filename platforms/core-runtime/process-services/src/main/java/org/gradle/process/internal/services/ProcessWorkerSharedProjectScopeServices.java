/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.process.internal.services;

import org.gradle.internal.reflect.Instantiator;
import org.jspecify.annotations.NullMarked;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.process.ExecOperations;
import org.gradle.process.internal.DefaultExecOperations;
import org.gradle.process.internal.ExecFactory;

@NullMarked
public class ProcessWorkerSharedProjectScopeServices implements ServiceRegistrationProvider {
    @Provides
    ExecOperations createExecOperations(Instantiator instantiator, ExecFactory execFactory) {
        return instantiator.newInstance(DefaultExecOperations.class, execFactory);
    }
}
