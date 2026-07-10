/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.buildinit.specs;

import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;

/**
 * Generates a new project from a configured {@link BuildInitConfig}.
 *
 * @implSpec Implementations should be {@code public}, {@code abstract} and provide an (implicit or explicit)
 * 0-argument constructor, as Gradle will instantiate them and inject any specified services.  Generators are
 * <strong>NOT</strong> expected to generate Gradle wrapper files.
 * @since 8.12
 */
@Incubating
public interface BuildInitGenerator {
    /**
     * Generates a project from the given configuration in the given directory.
     *
     * @param config the configuration for the project to generate
     * @param projectDir the directory that will contain the new project
     * @since 8.12
     */
    void generate(BuildInitConfig config, Directory projectDir);
}
