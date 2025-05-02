/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.model.eclipse;

import java.io.File;

/**
 * Information about a project in the eclipse workspace.
 *
 * @since 5.5
 */
public interface EclipseWorkspaceProject {
    /**
     * The name of the eclipse project
     *
     * @return the name of the project, never null
     */
    String getName();

    /**
     * The filesystem location of the eclipse project
     *
     * @return the location of the eclipse project, may be null in case of remote projects
     */
    File getLocation();

    /**
     * The status of the eclipse project.
     *
     * @since 5.6
     */
    boolean isOpen();
}
