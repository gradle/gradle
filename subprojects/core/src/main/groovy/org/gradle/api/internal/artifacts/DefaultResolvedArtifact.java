/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.resolve.DownloadOptions;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedArtifact implements ResolvedArtifact {
    private ResolvedDependency resolvedDependency;
    private Artifact artifact;
    private ResolveEngine resolvedEngine;
    private File file;

    public DefaultResolvedArtifact(Artifact artifact, ResolveEngine resolvedEngine) {
        this.artifact = artifact;
        this.resolvedEngine = resolvedEngine;
    }

    public ResolvedDependency getResolvedDependency() {
        return resolvedDependency;
    }

    public void setResolvedDependency(ResolvedDependency resolvedDependency) {
        this.resolvedDependency = resolvedDependency;
    }

    @Override
    public String toString() {
        return String.format("%s;%s", resolvedDependency, artifact.getName());
    }

    public String getName() {
        return artifact.getName();
    }

    public String getType() {
        return artifact.getType();
    }

    public String getExtension() {
        return artifact.getExt();
    }

    public String getVersion() {
        return getResolvedDependency() == null ? null : getResolvedDependency().getModuleVersion();
    }

    public String getDependencyName() {
        return getResolvedDependency() == null ? null : getResolvedDependency().getModuleName();
    }

    public File getFile() {
        if (file == null) {
            file = resolvedEngine.download(artifact, new DownloadOptions()).getLocalFile();
        }
        return file;
    }
}
