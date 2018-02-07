/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import java.util.Collection;

public class RegisteredProject {
    public final LocalComponentMetadata metaData;
    public final Collection<LocalComponentArtifactMetadata> artifacts;

    public RegisteredProject(LocalComponentMetadata metaData, Collection<LocalComponentArtifactMetadata> artifacts) {
        this.metaData = metaData;
        this.artifacts = artifacts;
    }
}
