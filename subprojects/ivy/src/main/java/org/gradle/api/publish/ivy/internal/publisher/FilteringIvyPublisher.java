/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publisher;

import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.publish.ivy.IvyArtifact;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A IvyPublisher that filters all the invalid artifacts whose ignoreIfAbsent is true.
 */
public class FilteringIvyPublisher implements IvyPublisher {
    private final IvyPublisher delegate;

    public FilteringIvyPublisher(IvyPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(IvyNormalizedPublication publication, IvyArtifactRepository repository) {
        delegate.publish(new FilteringIvyNormalizedPublication(publication), repository);
    }

    private static class FilteringIvyNormalizedPublication implements IvyNormalizedPublication {
        private final IvyNormalizedPublication delegate;

        private FilteringIvyNormalizedPublication(IvyNormalizedPublication delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public IvyPublicationIdentity getProjectIdentity() {
            return delegate.getProjectIdentity();
        }

        @Override
        public File getIvyDescriptorFile() {
            return delegate.getIvyDescriptorFile();
        }

        @Override
        public Set<IvyArtifact> getAllArtifacts() {
            // uses LinkedHashSet to keep the entry order
            return delegate.getAllArtifacts().stream().filter(artifact -> {
                return !artifact.getIgnoreIfAbsent() || artifact.getFile().exists();
            }).collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
