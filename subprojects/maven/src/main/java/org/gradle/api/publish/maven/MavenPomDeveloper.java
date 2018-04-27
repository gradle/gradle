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

import java.util.Map;

/**
 * A developer of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 * @see MavenPomDeveloperSpec
 */
@Incubating
@HasInternalProtocol
public interface MavenPomDeveloper {

    /**
     * Sets the unique ID of this developer in the SCM.
     */
    void setId(String id);

    /**
     * Sets the name of this contributor.
     */
    void setName(String name);

    /**
     * Sets the email
     */
    void setEmail(String email);

    /**
     * Sets the URL of this contributor.
     */
    void setUrl(String url);

    /**
     * Sets the organization name of this contributor.
     */
    void setOrganization(String organization);

    /**
     * Sets the organization's URL of this contributor.
     */
    void setOrganizationUrl(String organizationUrl);

    /**
     * Adds the specified roles to this contributor.
     */
    void roles(String... roles);

    /**
     * Sets the timezone of this contributor.
     */
    void setTimezone(String timezone);

    /**
     * Adds the specified properties to this contributor.
     */
    void properties(Map<String, String> properties);

}
