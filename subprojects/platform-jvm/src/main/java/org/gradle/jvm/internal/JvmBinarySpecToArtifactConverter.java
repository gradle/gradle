/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.jvm.internal;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.LibraryComponentIdentifier;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetaData;
import org.gradle.internal.component.model.BinarySpecToArtifactConverter;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.tasks.Jar;

public class JvmBinarySpecToArtifactConverter implements BinarySpecToArtifactConverter<JvmBinarySpec> {
    @Override
    public Class<JvmBinarySpec> getType() {
        return JvmBinarySpec.class;
    }

    @Override
    public ComponentArtifactMetaData convertArtifact(LibraryComponentIdentifier libraryId, JvmBinarySpec artifact) {
        Jar jar = artifact.getTasks().getJar();
        PublishArtifact publishArtifact = new ArchivePublishArtifact(jar);
        return new PublishArtifactLocalArtifactMetaData(libraryId, libraryId.getDisplayName(), libraryId.getDisplayName(),
            publishArtifact);
    }
}
