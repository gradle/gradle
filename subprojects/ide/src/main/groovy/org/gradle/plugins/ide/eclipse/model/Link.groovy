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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.plugins.ide.eclipse.model.internal.PathUtil

class Link {
    String name
    String type
    String location
    String locationUri

    def Link(String name, String type, String location, String locationUri) {
        assert name
        assert type
        assert location || locationUri
        assert !location || !locationUri
        this.name = name;
        this.type = type;
        this.location = PathUtil.normalizePath(location);
        this.locationUri = locationUri;
    }


    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Link link = (Link) o;

        if (location != link.location) { return false }
        if (locationUri != link.locationUri) { return false }
        if (name != link.name) { return false }
        if (type != link.type) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (location != null ? location.hashCode() : 0);
        result = 31 * result + (locationUri != null ? locationUri.hashCode() : 0);
        return result;
    }
}
