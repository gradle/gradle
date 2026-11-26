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

package org.gradle.testing.jacoco.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link JacocoBase}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface JacocoBaseInputs {

    /**
     * The classpath containing the JaCoCo classes used by the task.
     *
     * @see JacocoBase#getJacocoClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getJacocoClasspath();
}
