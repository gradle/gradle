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
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPomRelocation;

import javax.inject.Inject;

public class DefaultMavenPomRelocation implements MavenPomRelocation {

    private final Property<String> groupId;
    private final Property<String> artifactId;
    private final Property<String> version;
    private final Property<String> message;

    @Inject
    public DefaultMavenPomRelocation(ObjectFactory objectFactory) {
        groupId = objectFactory.property(String.class);
        artifactId = objectFactory.property(String.class);
        version = objectFactory.property(String.class);
        message = objectFactory.property(String.class);
    }

    @Override
    public Property<String> getGroupId() {
        return groupId;
    }

    @Override
    public Property<String> getArtifactId() {
        return artifactId;
    }

    @Override
    public Property<String> getVersion() {
        return version;
    }

    @Override
    public Property<String> getMessage() {
        return message;
    }

}
