/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.util.Path;

import java.io.IOException;

/**
 * A thread-safe, reusable serializer for {@link ProjectIdentity}.
 */
public class ProjectIdentitySerializer extends AbstractSerializer<ProjectIdentity> {

    private final PathSerializer pathSerializer;

    public ProjectIdentitySerializer(PathSerializer pathSerializer) {
        this.pathSerializer = pathSerializer;
    }

    @Override
    public ProjectIdentity read(Decoder decoder) throws IOException {
        Path buildPath = pathSerializer.read(decoder);

        boolean isRoot = decoder.readBoolean();

        if (isRoot) {
            String projectName = decoder.readString();
            return ProjectIdentity.forRootProject(buildPath, projectName);
        } else {
            Path projectPath = pathSerializer.read(decoder);
            return ProjectIdentity.forSubproject(buildPath, projectPath);
        }
    }

    @Override
    public void write(Encoder encoder, ProjectIdentity value) throws IOException {
        pathSerializer.write(encoder, value.getBuildPath());

        boolean isRoot = value.getProjectPath().equals(Path.ROOT);
        encoder.writeBoolean(isRoot);

        if (isRoot) {
            encoder.writeString(value.getProjectName());
        } else {
            pathSerializer.write(encoder, value.getProjectPath());
        }
    }

}
