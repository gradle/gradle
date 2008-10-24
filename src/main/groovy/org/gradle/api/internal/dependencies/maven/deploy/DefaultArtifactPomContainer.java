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
package org.gradle.api.internal.dependencies.maven.deploy;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.util.WrapUtil;
import org.gradle.api.InvalidUserDataException;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactPomContainer implements ArtifactPomContainer {
    private File pomDir;

    private Map<String, ArtifactPom> artifactPoms = new HashMap<String, ArtifactPom>();

    private ArtifactPom defaultArtifactPom;

    public DefaultArtifactPomContainer(File pomDir) {
        this.pomDir = pomDir;
    }

    public void addArtifact(Artifact artifact, File src) {
        for (ArtifactPom activeArtifactPom : getActiveArtifactPoms()) {
            activeArtifactPom.addArtifact(artifact, src);
        }
    }

    public ArtifactPom getDefaultArtifactPom() {
        return defaultArtifactPom;
    }

    public void setDefaultArtifactPom(ArtifactPom defaultArtifactPom) {
        this.defaultArtifactPom = defaultArtifactPom;
    }

    public void addArtifactPom(ArtifactPom artifactPom) {
        if (artifactPom == null) {
            throw new InvalidUserDataException("ArtifactPom must not be null.");
        }
        artifactPoms.put(artifactPom.getName(), artifactPom);
    }

    public ArtifactPom getArtifactPom(String name) {
        return artifactPoms.get(name);
    }

    public Map<File, File> createDeployableUnits() {
        Map<File, File> deployableUnits = new HashMap<File, File>();
        for (ArtifactPom activeArtifactPom : getActiveArtifactPoms()) {
            if (activeArtifactPom.getArtifactFile() != null) {
                File pomFile = createPomFile(activeArtifactPom.getName());
                activeArtifactPom.toPomFile(pomFile);
                deployableUnits.put(pomFile, activeArtifactPom.getArtifactFile());
            }
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

    private Iterable<ArtifactPom> getActiveArtifactPoms() {
        Iterable<ArtifactPom> activeArtifactPoms;
        if (artifactPoms.size() == 0 && defaultArtifactPom != null) {
            activeArtifactPoms = WrapUtil.toSet(defaultArtifactPom);
        } else {
            activeArtifactPoms = artifactPoms.values();
        }
        return activeArtifactPoms;
    }
}
