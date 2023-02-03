/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Link.
 */
public class Link {

    private String name;
    private String type;
    private String location;
    private String locationUri;

    public Link(String name, String type, String location, String locationUri) {
        Preconditions.checkArgument(!isNullOrEmpty(name));
        Preconditions.checkArgument(!isNullOrEmpty(type));
        Preconditions.checkArgument(isNullOrEmpty(location) || isNullOrEmpty(locationUri));
        Preconditions.checkArgument(!isNullOrEmpty(location) || !isNullOrEmpty(locationUri));
        this.name = name;
        this.type = type;
        this.location = PathUtil.normalizePath(emptyToNull(location));
        this.locationUri = emptyToNull(locationUri);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocationUri() {
        return locationUri;
    }

    public void setLocationUri(String locationUri) {
        this.locationUri = locationUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        Link link = (Link) o;
        return Objects.equal(name, link.name)
            && Objects.equal(type, link.type)
            && Objects.equal(location, link.location)
            && Objects.equal(locationUri, link.locationUri);
    }

    @Override
    public int hashCode() {
        int result;
        result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (location != null ? location.hashCode() : 0);
        result = 31 * result + (locationUri != null ? locationUri.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Link{"
            + "name='" + name + '\''
            + ", type='" + type + '\''
            + ", location='" + location + '\''
            + ", locationUri='" + locationUri + '\''
            + '}';
    }
}
