/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish.maven.deploy;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.internal.artifacts.publish.maven.PomFileWriter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactPomContainer implements ArtifactPomContainer {
    private File pomDir;

    private Map<String, ArtifactPom> artifactPoms = new HashMap<String, ArtifactPom>();
    private PomFilterContainer pomFilterContainer;
    private PomFileWriter pomFileWriter;
    private ArtifactPomFactory artifactPomFactory;

    public DefaultArtifactPomContainer(File pomDir, PomFilterContainer pomFilterContainer, 
                                       PomFileWriter pomFileWriter, ArtifactPomFactory artifactPomFactory) {
        this.pomDir = pomDir;
        this.pomFilterContainer = pomFilterContainer;
        this.pomFileWriter = pomFileWriter;
        this.artifactPomFactory = artifactPomFactory;
    }

    public void addArtifact(Artifact artifact, File src) {
        if (artifact == null || src == null) {
            throw new InvalidUserDataException("Artifact or source file must not be null!");
        }
        for (PomFilter activePomFilter : pomFilterContainer.getActivePomFilters()) {
            if (activePomFilter.getFilter().accept(artifact, src)) {
                throwExceptionIfMultipleArtifactsPerPom(activePomFilter.getName(), artifact, src);
                artifactPoms.put(activePomFilter.getName(), artifactPomFactory.createArtifactPom(activePomFilter.getPomTemplate(), artifact, src));
            }
        }
    }

    private void throwExceptionIfMultipleArtifactsPerPom(String name, Artifact artifact, File src) {
        if (artifactPoms.get(name) != null) {
            throw new InvalidUserDataException(String.format("There can be only one artifact per pom. Artifact %s with file %s can't be assigned to pom %s",
                    artifact.getName(), src.getAbsolutePath(), name));
        }
    }

    public Map<File, File> createDeployableUnits(Set<Configuration> configurations) {
        Map<File, File> deployableUnits = new HashMap<File, File>();
        for (String activeArtifactPomName : artifactPoms.keySet()) {
            ArtifactPom activeArtifactPom = artifactPoms.get(activeArtifactPomName);
            File pomFile = createPomFile(activeArtifactPomName);
            pomFileWriter.write(activeArtifactPom.getPom(), configurations, pomFile);
            deployableUnits.put(pomFile, activeArtifactPom.getArtifactFile());
        }
        return deployableUnits;
    }

    private File createPomFile(String artifactPomName) {
        return new File(pomDir, "pom-" + artifactPomName + ".xml");
    }

    public File getPomDir() {
        return pomDir;
    }

    public void setPomDir(File pomDir) {
        this.pomDir = pomDir;
    }

    public Map<String, ArtifactPom> getArtifactPoms() {
        return artifactPoms;
    }
}
