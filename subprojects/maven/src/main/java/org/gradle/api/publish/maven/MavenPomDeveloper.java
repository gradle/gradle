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

import java.util.Map;
import java.util.Set;

/**
 * A developer of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 * @see MavenPomDeveloperSpec
 */
@Incubating
public interface MavenPomDeveloper {

    /**
     * Returns the unique ID of this developer in the SCM.
     */
    String getId();

    /**
     * Sets the unique ID of this developer in the SCM.
     */
    void setId(String id);

    /**
     * Returns the name of this contributor.
     */
    String getName();

    /**
     * Sets the name of this contributor.
     */
    void setName(String name);

    /**
     * Returns the email of this contributor.
     */
    String getEmail();

    /**
     * Sets the email
     */
    void setEmail(String email);

    /**
     * Returns the URL of this contributor.
     */
    String getUrl();

    /**
     * Sets the URL of this contributor.
     */
    void setUrl(String url);

    /**
     * Returns the organization name of this contributor.
     */
    String getOrganization();

    /**
     * Sets the organization name of this contributor.
     */
    void setOrganization(String organization);

    /**
     * Returns the organization's URL of this contributor.
     */
    String getOrganizationUrl();

    /**
     * Sets the organization's URL of this contributor.
     */
    void setOrganizationUrl(String organizationUrl);

    /**
     * Returns the roles of this contributor.
     */
    Set<String> getRoles();

    /**
     * Adds the specified roles to this contributor.
     */
    void roles(String... roles);

    /**
     * Returns the timezone of this contributor.
     */
    String getTimezone();

    /**
     * Sets the timezone of this contributor.
     */
    void setTimezone(String timezone);

    /**
     * Returns the properties of this contributor.
     */
    Map<String, String> getProperties();

    /**
     * Adds the specified properties to this contributor.
     */
    void properties(Map<String, String> properties);

}
