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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.StartParameterInternal;

import java.io.File;

@ServiceScope(Scope.CrossBuildSession.class)
public class CrossBuildSessionParameters {
    private final StartParameterInternal startParameter;
    private final File userActionRootDir;

    public CrossBuildSessionParameters(StartParameterInternal startParameterInternal, File userActionRootDir) {
        this.startParameter = startParameterInternal;
        this.userActionRootDir = userActionRootDir;
    }

    public StartParameterInternal getStartParameter() {
        return startParameter;
    }

    /**
     * Root directory of the "root-root build", determined by the top-level build request parameters.
     * <p>
     * The "root-root build" is the root build of the root build tree.
     * Nested build trees exist due to how the special {@code GradleBuild} task works
     * and also how pre-compiled Kotlin script plugin accessor generation is implemented.
     * Each nested build tree technically has its own root build.
     * <p>
     * The user-action root directory is the origin point from which the rest of the Gradle invocation stems.
     * The directory is either provided explicitly via the Tooling API connection
     * or discovered in the file system, based on where the user invoked Gradle.
     * <p>
     * The evaluation of settings and projects of the root-root build determines whether nested build trees are required at all.
     * Also, if Gradle is invoked in the {@code --continuous} mode, it will be the root-root build that gets re-executed in a build session.
     */
    public File getUserActionRootDirectory() {
        return userActionRootDir;
    }
}
