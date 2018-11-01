/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

/**
 * A set of artifacts to be published.
 */
public interface PublishArtifactSet extends DomainObjectSet<PublishArtifact>, Buildable {
    /**
     * Registers a file for publication.
     *
     * @param artifactFile provider to file to register
     * @param configuration action to configure a publish artifact
     * @return a Provider to the publish artifact
     * @since 5.1
     */
    @Incubating
    Provider<PublishArtifact> register(Provider<? extends FileSystemLocation> artifactFile, Action<ConfigurablePublishArtifact> configuration);

    FileCollection getFiles();
}
