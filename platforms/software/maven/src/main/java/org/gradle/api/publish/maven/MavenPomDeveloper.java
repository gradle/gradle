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

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * A developer of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 * @see MavenPomDeveloperSpec
 */
public interface MavenPomDeveloper {

    /**
     * The unique ID of this developer in the SCM.
     */
    @Input
    @Optional
    Property<String> getId();

    /**
     * The name of this developer.
     */
    @Input
    @Optional
    Property<String> getName();

    /**
     * The email
     */
    @Input
    @Optional
    Property<String> getEmail();

    /**
     * The URL of this developer.
     */
    @Input
    @Optional
    Property<String> getUrl();

    /**
     * The organization name of this developer.
     */
    @Input
    @Optional
    Property<String> getOrganization();

    /**
     * The organization's URL of this developer.
     */
    @Input
    @Optional
    Property<String> getOrganizationUrl();

    /**
     * The roles of this developer.
     */
    @Input
    SetProperty<String> getRoles();

    /**
     * The timezone of this developer.
     */
    @Input
    @Optional
    Property<String> getTimezone();

    /**
     * The properties of this developer.
     */
    @Input
    MapProperty<String, String> getProperties();

}
