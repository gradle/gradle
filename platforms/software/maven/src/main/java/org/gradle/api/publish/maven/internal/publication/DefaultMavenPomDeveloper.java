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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomDeveloper;

import javax.inject.Inject;

public class DefaultMavenPomDeveloper implements MavenPomDeveloper, MavenPomContributor {

    private final Property<String> id;
    private final Property<String> name;
    private final Property<String> email;
    private final Property<String> url;
    private final Property<String> organization;
    private final Property<String> organizationUrl;
    private final SetProperty<String> roles;
    private final Property<String> timezone;
    private final MapProperty<String, String> properties;

    @Inject
    public DefaultMavenPomDeveloper(ObjectFactory objectFactory) {
        id = objectFactory.property(String.class);
        name = objectFactory.property(String.class);
        email = objectFactory.property(String.class);
        url = objectFactory.property(String.class);
        organization = objectFactory.property(String.class);
        organizationUrl = objectFactory.property(String.class);
        roles = objectFactory.setProperty(String.class);
        timezone = objectFactory.property(String.class);
        properties = objectFactory.mapProperty(String.class, String.class);
    }

    @Override
    public Property<String> getId() {
        return id;
    }

    @Override
    public Property<String> getName() {
        return name;
    }

    @Override
    public Property<String> getEmail() {
        return email;
    }

    @Override
    public Property<String> getUrl() {
        return url;
    }

    @Override
    public Property<String> getOrganization() {
        return organization;
    }

    @Override
    public Property<String> getOrganizationUrl() {
        return organizationUrl;
    }

    @Override
    public SetProperty<String> getRoles() {
        return roles;
    }

    @Override
    public Property<String> getTimezone() {
        return timezone;
    }

    @Override
    public MapProperty<String, String> getProperties() {
        return properties;
    }

}
