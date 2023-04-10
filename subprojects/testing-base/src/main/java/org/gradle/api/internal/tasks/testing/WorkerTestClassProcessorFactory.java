/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.tasks.Internal;
import org.gradle.internal.service.ServiceRegistry;

public interface WorkerTestClassProcessorFactory {
    TestClassProcessor create(ServiceRegistry serviceRegistry);

    /**
     * defines test-class stealing strategy:
     * <ul>
     *     <li>UNSUPPORTED: discard stealer (return null)</li>
     *     <li>TESTWORKER: just return stealer<br>
     *          stealing will be handled by {@link org.gradle.api.internal.tasks.testing.worker.TestWorker TestWorker}<br<
     *          {@link TestClassProcessor#processTestClass} called by {@link org.gradle.api.internal.tasks.testing.worker.TestWorker TestWorker} must be run synchron</li>
     *     <li>DElEGATED: return null, but store stealer to be used at the appropriate point </li>
     * </ul>
     *
     * will be called before create, but only if stealer is not null
     *
     * @return TestClassStealer to be used by {@link org.gradle.api.internal.tasks.testing.worker.TestWorker TestWorker}
     */
    @Internal
    RemoteTestClassStealer buildWorkerStealer(RemoteTestClassStealer stealer);

}
