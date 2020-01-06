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

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Information about the eclipse instance.
 *
 * This is a possible parameter for the {@link EclipseProject} model resolution.
 * It provides additional information to gradle so it can build a more accurate {@link EclipseProject} model.
 *
 * @see EclipseProject
 * @see org.gradle.tooling.BuildController#getModel(Class, Class, Action)
 * @since 5.5
 */
@Incubating
public interface EclipseRuntime {
    EclipseWorkspace getWorkspace();

    /**
     * The eclipse workspace
     */
    void setWorkspace(EclipseWorkspace eclipseWorkspace);

}
