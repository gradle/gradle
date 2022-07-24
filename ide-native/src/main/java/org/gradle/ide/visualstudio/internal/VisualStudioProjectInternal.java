/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.plugins.ide.internal.IdeProjectMetadata;

public interface VisualStudioProjectInternal extends VisualStudioProject {
    /**
     * Returns the name of the component associated with this project
     */
    @Input
    String getComponentName();

    /**
     * Adds tasks required to build this component.
     */
    void builtBy(Object... tasks);

    /**
     * Returns a {@link IdeProjectMetadata} view of this project
     */
    @Internal
    IdeProjectMetadata getPublishArtifact();
}
