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

package org.gradle.api.initialization;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.TaskReference;
import org.gradle.internal.HasInternalProtocol;

import java.io.File;

/**
 * A build that is included in the composite.
 *
 * @since 3.1
 */
@Incubating
@HasInternalProtocol
public interface IncludedBuild {
    /**
     * The name of the included build.
     */
    String getName();

    /**
     * The root directory of the included build.
     */
    File getProjectDir();

    /**
     * Produces a reference to a task in the included build.
     */
    TaskReference task(String path);
}
