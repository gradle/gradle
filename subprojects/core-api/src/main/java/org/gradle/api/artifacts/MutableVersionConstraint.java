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
import org.gradle.internal.HasInternalProtocol;

/**
 * A configurable version constraint. This is exposed to the build author, so that one can express
 * more constraints on a version,
 *
 * @since 4.4
 */
@Incubating
@HasInternalProtocol
public interface MutableVersionConstraint extends VersionConstraint {

    /**
     * Sets the preferred version of this module. Any other rejection/strict constraint will be overriden.
     * @param version the preferred version of this module
     */
    void prefer(String version);

    /**
     * Sets the version as strict, meaning that if any other dependency version for this module disagrees with
     * this version, resolution will fail.
     *
     * @param version the strict version to be used for this module
     */
    void strictly(String version);

}
