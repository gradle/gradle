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

package org.gradle.api.internal.tasks.testing.retrying;

import org.gradle.api.internal.tasks.testing.FrameworkTestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.WorkerFrameworkTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.internal.service.ServiceRegistry;

import java.io.Serializable;

public class UntilFailureWorkerTestClassProcessorFactory implements WorkerTestClassProcessorFactory, Serializable {

    private static final long serialVersionUID = 42L;

    private final WorkerFrameworkTestClassProcessorFactory frameworkProcessorFactory;
    private final long untilFailureRunCount;

    public UntilFailureWorkerTestClassProcessorFactory(WorkerFrameworkTestClassProcessorFactory frameworkProcessorFactory, long untilFailureRunCount) {
        this.frameworkProcessorFactory = frameworkProcessorFactory;
        this.untilFailureRunCount = untilFailureRunCount;
    }

    @Override
    public TestClassProcessor create(ServiceRegistry serviceRegistry) {
        FrameworkTestClassProcessor frameworkProcessor = frameworkProcessorFactory.create(serviceRegistry);
        return new UntilFailureTestClassProcessor(frameworkProcessor, untilFailureRunCount);
    }
}
