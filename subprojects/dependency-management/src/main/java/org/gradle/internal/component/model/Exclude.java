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

package org.gradle.internal.component.model;

import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.plugins.matcher.PatternMatcher;

public interface Exclude {

    /**
     * Returns the id of the described artifact, without revision information
     *
     * @return
     */
    public ArtifactId getId();

    /**
     * Returns the configurations of the module in which the artifact is asked
     *
     * @return an array of configuration names in which the artifact is asked
     */
    public String[] getConfigurations();

    /**
     * Returns the matcher to use to know if an artifact match the current descriptor
     *
     * @return
     */
    public PatternMatcher getMatcher();

}
