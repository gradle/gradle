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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents a constrained version. By default, when a dependency has a version number, it is assumed
 * that the version can be upgraded during conflict resolution (typically, version 1.15 can be upgraded to 1.16). However
 * in some cases we don't want this behavior. This class represents the base spec of module constraints.
 * @since 4.4
 */
@Incubating
public interface VersionConstraint {
    /**
     * The preferred version of a module. The preferred version of a module can typically be upgraded during dependency resolution,
     * unless further constraints are added.
     *
     * @return the baseline version, often referred to as the preferred version.
     */
    @Nullable
    String getPreferredVersion();

    /**
     * Returns the list of versions that this module rejects  (which may be exact versions, or ranges, anything that fits into a version string).
     *
     * @return the list of rejected versions
     */
    List<String> getRejectedVersions();
}
