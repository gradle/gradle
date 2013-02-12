/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher;

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.maven.InvalidMavenPublicationException;
import org.gradle.api.publish.maven.internal.MavenProjectIdentity;
import org.gradle.util.GUtil;

public class ValidatingMavenPublisher implements MavenPublisher {
    private static final java.lang.String ID_REGEX = "[A-Za-z0-9_\\-.]+";
    private final MavenPublisher delegate;

    public ValidatingMavenPublisher(MavenPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        validate(publication.getProjectIdentity());

        delegate.publish(publication, artifactRepository);
    }

    private void validate(MavenProjectIdentity projectIdentity) {
        checkValidMavenIdentifier("groupId", projectIdentity.getGroupId());
        checkValidMavenIdentifier("artifactId", projectIdentity.getArtifactId());
        checkNonEmpty("version", projectIdentity.getVersion());
    }

    private void checkValidMavenIdentifier(String name, String value) {
        checkNonEmpty(name, value);
        if (!value.matches(ID_REGEX)) {
            throw new InvalidMavenPublicationException(String.format("The %s value is not a valid Maven identifier (%s)", name, ID_REGEX));
        }
    }

    private void checkNonEmpty(String name, String value) {
        if (!GUtil.isTrue(value)) {
            throw new InvalidMavenPublicationException(String.format("The %s value cannot be empty", name));
        }
    }
}
