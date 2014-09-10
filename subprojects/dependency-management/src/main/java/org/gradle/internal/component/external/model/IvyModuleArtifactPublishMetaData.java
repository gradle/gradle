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

package org.gradle.internal.component.external.model;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

// TODO:ADAM - This is actually Ivy artifact publish meta data
public interface IvyModuleArtifactPublishMetaData {
    ModuleComponentArtifactIdentifier getId();

    /**
     * Converts this artifact to an Ivy artifact. This method is here while we transition away from the Ivy types.
     */
    Artifact toIvyArtifact();

    IvyArtifactName getArtifactName();

    File getFile();
}
