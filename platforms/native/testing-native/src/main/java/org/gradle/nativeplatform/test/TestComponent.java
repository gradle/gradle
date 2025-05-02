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

package org.gradle.nativeplatform.test;

import org.gradle.api.Task;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.provider.Provider;

/**
 * Represents a component that can run tests.
 *
 * @since 4.5
 */
public interface TestComponent extends SoftwareComponent {
    /**
     * Returns the task that runs the tests for this component.
     */
    Provider<? extends Task> getRunTask();
}
