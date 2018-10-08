/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy;

import org.gradle.internal.HasInternalProtocol;

/**
 * A module dependency declared in an ivy dependency descriptor published as part of an {@link IvyPublication}.
 */
@HasInternalProtocol
public interface IvyDependency {

    /**
     * The organisation value for this dependency.
     */
    String getOrganisation();

    /**
     * The module value for this dependency.
     */
    String getModule();

    /**
     * The revision value for this dependency.
     */
    String getRevision();

    /**
     * The configuration mapping string that will be output for this dependency.
     * A null value indicates that no "conf" attribute will be written for this dependency.
     *
     * @return the configuration mapping
     */
    String getConfMapping();

    /**
     * The transitive value for this dependency.
     *
     * @since 3.1
     */
    boolean isTransitive();
}
