/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.results;

import javax.annotation.Nullable;
import java.util.List;

public interface ScenarioDefinition {
    /**
     * A human consumable display name for this definition.
     */
    String getDisplayName();

    /**
     * The test project name.
     */
    String getTestProject();

    /**
     * The tasks executed.
     */
    List<String> getTasks();

    /**
     * The clean tasks executed.
     */
    List<String> getCleanTasks();

    /**
     * The Gradle arguments.
     */
    List<String> getArgs();

    /**
     * The Gradle JVM args. Null if not known
     */
    @Nullable
    List<String> getGradleOpts();

    /**
     * Was the daemon used. Null if not known
     */
    @Nullable
    Boolean getDaemon();
}
