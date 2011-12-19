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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

import java.io.File;

public class DefaultModuleVersionDescriptor implements ModuleVersionDescriptor {
    private final ModuleDescriptor moduleDescriptor;
    private final Artifact metadataArtifact;
    private final File metadataFile;
    private final boolean changing;

    public DefaultModuleVersionDescriptor(ModuleDescriptor moduleDescriptor, Artifact metadataArtifact, File metadataFile, boolean changing) {
        this.moduleDescriptor = moduleDescriptor;
        this.metadataArtifact = metadataArtifact;
        this.metadataFile = metadataFile;
        this.changing = changing;
    }

    public ModuleRevisionId getId() throws ModuleVersionResolveException {
        return moduleDescriptor.getResolvedModuleRevisionId();
    }

    public ModuleDescriptor getDescriptor() throws ModuleVersionResolveException {
        return moduleDescriptor;
    }
    
    public Artifact getMetadataArtifact() {
        return metadataArtifact;
    }

    public File getMetadataFile() {
        return metadataFile;
    }

    public boolean isChanging() {
        return changing;
    }
}
