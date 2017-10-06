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

package org.gradle.vcs;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ComponentSelector;

/**
 * A dependency mapping provided by a VCS repository.
 *
 * @since 4.4
 */
@Incubating
public interface VcsMapping {
    /**
     * The requested dependency, before it is resolved.
     * The requested dependency does not change even if there are multiple dependency substitution rules
     * that manipulate the dependency metadata.
     */
    ComponentSelector getRequested();

    void from(VersionControlSpec versionControlSpec);
}
