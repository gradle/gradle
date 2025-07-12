/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.model.gradle;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.Model;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.List;

/**
 * Represents the Gradle API classpath for a build.
 *
 * This model provides access to the classpath that contains the Gradle API classes.
 *
 * @since 9.1.0
 */
@NullMarked
@Incubating
public interface GradleApiModel extends Model {

    /**
     * Returns the classpath containing the Gradle API classes.
     *
     * @since 9.1.0
     */
    @Incubating
    List<File> getGradleApiClasspath();

}
