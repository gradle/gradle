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

import org.gradle.api.publish.internal.PublicationInternal;

import java.util.Objects;

/**
 * A publication associated with a project's display text, for better error feedback.
 */
final class PublicationWithProject {
    private final String projectDisplayText;
    private final PublicationInternal<?> publication;

    PublicationWithProject(String projectDisplayText, PublicationInternal<?> publication) {
        this.projectDisplayText = projectDisplayText;
        this.publication = publication;
    }

    public PublicationInternal<?> getPublication() {
        return publication;
    }

    @Override
    public String toString() {
        return "'" + publication.getName() + "' in " + projectDisplayText;
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
        return Objects.equals(projectDisplayText, that.projectDisplayText) && Objects.equals(publication, that.publication);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectDisplayText, publication);
    }
}
