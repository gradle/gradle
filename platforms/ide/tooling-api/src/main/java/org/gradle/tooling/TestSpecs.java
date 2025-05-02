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

package org.gradle.tooling;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Provides test selection from a specific test task.
 *
 * @see TestLauncher#withTestsFor(Action)
 * @since 7.6
 */
@Incubating
public interface TestSpecs {

    /**
     * Creates a new test selection for the target task.
     *
     * @param taskPath The target task.
     * @return A new test selection.
     */
    TestSpec forTaskPath(String taskPath);
}
