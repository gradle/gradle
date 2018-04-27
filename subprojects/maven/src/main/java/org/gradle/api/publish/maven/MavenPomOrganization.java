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

package org.gradle.api.publish.maven;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * The organization of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 */
@Incubating
@HasInternalProtocol
public interface MavenPomOrganization {

    /**
     * Sets the name of this organization.
     */
    void setName(String name);

    /**
     * Sets the URL of this organization.
     */
    void setUrl(String url);

}
