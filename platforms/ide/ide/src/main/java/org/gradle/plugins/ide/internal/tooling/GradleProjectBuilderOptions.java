/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.NonNullApi;

@NonNullApi
public class GradleProjectBuilderOptions {

    /**
     * Determines whether a builder for the {@link org.gradle.tooling.model.GradleProject} model should realize tasks.
     * <p>
     * This method has to be invoked during builder execution time to provide an effective value.
     * That is due to Android Studio (via Intellij IDEA) using {@code LongRunningOperation.withArguments()} to configure the value of the option.
     */
    public static boolean shouldRealizeTasks() {
        // This property was initially added in Gradle 6.1 to allow Android Studio troubleshoot sync performance issues.
        // As Android Studio wanted to avoid task realization during sync, it started using "omit_all_tasks" option in production.
        // Gradle should support this option at least until an alternative solution exists and Android Studio has migrated to it.
        String builderOptions = System.getProperty("org.gradle.internal.GradleProjectBuilderOptions", "");
        boolean avoidTaskRealization = "omit_all_tasks".equals(builderOptions);
        return !avoidTaskRealization;
    }

}
