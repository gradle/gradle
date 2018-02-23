/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Incubating;

/**
 * An Eclipse project nature definition.
 *
 * @since 2.9
 */
@Incubating
public interface EclipseProjectNature {

    /**
     * Returns the unique identifier of the project nature.
     *  @see <a href="http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_core_resources_natures.html">Definition of project natures in the Eclipse documentation</a>
     *
     * @return The project nature id.
     */
    String getId();
}
