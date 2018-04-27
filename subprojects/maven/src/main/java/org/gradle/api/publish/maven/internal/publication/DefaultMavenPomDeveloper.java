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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DefaultMavenPomDeveloper implements MavenPomDeveloperInternal, MavenPomContributorInternal {

    private String id;
    private String name;
    private String email;
    private String url;
    private String organization;
    private String organizationUrl;
    private final Set<String> roles = new LinkedHashSet<String>();
    private String timezone;
    private final Map<String, String> properties = new LinkedHashMap<String, String>();

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getOrganization() {
        return organization;
    }

    @Override
    public void setOrganization(String organization) {
        this.organization = organization;
    }

    @Override
    public String getOrganizationUrl() {
        return organizationUrl;
    }

    @Override
    public void setOrganizationUrl(String organizationUrl) {
        this.organizationUrl = organizationUrl;
    }

    @Override
    public void roles(String... roles) {
        this.roles.addAll(Arrays.asList(roles));
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String getTimezone() {
        return timezone;
    }

    @Override
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    @Override
    public void properties(Map<String, String> properties) {
        this.properties.putAll(properties);
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }
}
