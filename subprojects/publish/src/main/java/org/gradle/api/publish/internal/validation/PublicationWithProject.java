/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.publish.internal.validation;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.Objects;

/**
 * A publication associated with a project's display text, for better error feedback.
 */
final class PublicationWithProject {
    private final String projectDisplayText;
    private final String publicationName;
    private final ModuleVersionIdentifier coordinates;

    public PublicationWithProject(String projectDisplayText, String publicationName, ModuleVersionIdentifier coordinates) {
        this.projectDisplayText = projectDisplayText;
        this.publicationName = publicationName;
        this.coordinates = coordinates;
    }

    public ModuleVersionIdentifier getCoordinates() {
        return coordinates;
    }

    @Override
    public String toString() {
        return "'" + publicationName + "' in " + projectDisplayText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PublicationWithProject that = (PublicationWithProject) o;
        return Objects.equals(projectDisplayText, that.projectDisplayText) && Objects.equals(publicationName, that.publicationName) && Objects.equals(coordinates, that.coordinates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectDisplayText, publicationName, coordinates);
    }
}
