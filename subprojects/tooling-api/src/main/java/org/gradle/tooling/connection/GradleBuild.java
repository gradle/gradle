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

package org.gradle.tooling.connection;

import org.gradle.api.Incubating;

/**
 * Represents a participant in a composite.
 *
 */
@Incubating
public interface GradleBuild {
    /**
     * Build Identity to be used to correlate results.
     *
     * @return this build's identity, never null
     */
    BuildIdentity toBuildIdentity();

    /**
     * Project Identity to be used to correlate results.
     *
     * @param projectPath path to project in a Gradle build (e.g., :foo:bar)
     * @return identity for a project in this build with the given path
     */
    ProjectIdentity toProjectIdentity(String projectPath);
}
