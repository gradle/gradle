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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.file.FileSource;
import org.gradle.util.DeprecationLogger;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedArtifact implements ResolvedArtifact {
    private final ResolvedDependency resolvedDependency;
    private final Artifact artifact;
    private final FileSource artifactSource;
    private File file;

    public DefaultResolvedArtifact(ResolvedDependency resolvedDependency, Artifact artifact, FileSource artifactSource) {
        this.resolvedDependency = resolvedDependency;
        this.artifact = artifact;
        this.artifactSource = artifactSource;
    }

    public ResolvedDependency getResolvedDependency() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("ResolvedArtifact.getResolvedDependency()");
        return resolvedDependency;
    }

    public ResolvedModuleVersion getModuleVersion() {
        return resolvedDependency.getModule();
    }

    @Override
    public String toString() {
        return String.format("[ResolvedArtifact dependency:%s name:%s classifier:%s extension:%s type:%s]", resolvedDependency, getName(), getClassifier(), getExtension(), getType());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultResolvedArtifact other = (DefaultResolvedArtifact) obj;
        if (!other.resolvedDependency.getModule().getId().equals(resolvedDependency.getModule().getId())) {
            return false;
        }
        if (!other.getName().equals(getName())) {
            return false;
        }
        if (!other.getType().equals(getType())) {
            return false;
        }
        if (!other.getExtension().equals(getExtension())) {
            return false;
        }
        if (!other.artifact.getExtraAttributes().equals(artifact.getExtraAttributes())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return resolvedDependency.getModule().getId().hashCode() ^ getName().hashCode() ^ getType().hashCode() ^ getExtension().hashCode() ^ artifact.getExtraAttributes().hashCode();
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

    public String getClassifier() {
        return artifact.getExtraAttribute(Dependency.CLASSIFIER);
    }
    
    public File getFile() {
        if (file == null) {
            file = artifactSource.get();
        }
        return file;
    }
}
