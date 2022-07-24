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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;

public class ReadableMavenProjectIdentity implements MavenProjectIdentity {
    private final Provider<String> groupId;
    private final Provider<String> artifactId;
    private final Provider<String> version;

    public ReadableMavenProjectIdentity(String groupId, String artifactId, String version) {
        this.groupId = Providers.of(groupId);
        this.artifactId = Providers.of(artifactId);
        this.version = Providers.of(version);
    }

    @Override
    public Provider<String> getGroupId() {
        return groupId;
    }

    @Override
    public Provider<String> getArtifactId() {
        return artifactId;
    }

    @Override
    public Provider<String> getVersion() {
        return version;
    }
}
