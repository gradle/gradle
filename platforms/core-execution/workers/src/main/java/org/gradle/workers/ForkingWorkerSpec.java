/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers;

import org.gradle.api.Action;
import org.gradle.process.JavaForkOptions;

/**
 * A worker spec providing the requirements of a forked process.
 *
 * @since 5.6
 */
public interface ForkingWorkerSpec extends WorkerSpec {
    /**
     * Executes the provided action against the {@link JavaForkOptions} object associated with this builder.
     *
     * @param forkOptionsAction - An action to configure the {@link JavaForkOptions} for this builder
     */
    void forkOptions(Action<? super JavaForkOptions> forkOptionsAction);

    /**
     * Returns the {@link JavaForkOptions} object associated with this builder.
     *
     * @return the {@link JavaForkOptions} of this builder
     */
    JavaForkOptions getForkOptions();
}
