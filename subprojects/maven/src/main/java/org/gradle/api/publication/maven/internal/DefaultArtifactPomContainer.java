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
package org.gradle.api.publication.maven.internal;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.MavenDeployment;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.publish.maven.internal.publication.ReadableMavenProjectIdentity;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultArtifactPomContainer implements ArtifactPomContainer {
    private Map<String, ArtifactPom> artifactPoms = new HashMap<String, ArtifactPom>();
    private final MavenPomMetaInfoProvider pomMetaInfoProvider;
    private PomFilterContainer pomFilterContainer;
    private ArtifactPomFactory artifactPomFactory;

    public DefaultArtifactPomContainer(MavenPomMetaInfoProvider pomMetaInfoProvider, PomFilterContainer pomFilterContainer,
                                       ArtifactPomFactory artifactPomFactory) {
        this.pomMetaInfoProvider = pomMetaInfoProvider;
        this.pomFilterContainer = pomFilterContainer;
        this.artifactPomFactory = artifactPomFactory;
    }

    @Override
    public void addArtifact(Artifact artifact, File src) {
        if (artifact == null || src == null) {
            throw new InvalidUserDataException("Artifact or source file must not be null!");
        }
        for (PomFilter activePomFilter : pomFilterContainer.getActivePomFilters()) {
            if (activePomFilter.getFilter().accept(artifact, src)) {
                if (artifactPoms.get(activePomFilter.getName()) == null) {
                    artifactPoms.put(activePomFilter.getName(), artifactPomFactory.createArtifactPom(activePomFilter.getPomTemplate()));
                }
                artifactPoms.get(activePomFilter.getName()).addArtifact(artifact, src);
            }
        }
    }

    @Override
    public Set<MavenDeployment> createDeployableFilesInfos() {
        Set<MavenDeployment> mavenDeployments = new HashSet<MavenDeployment>();
        for (String activeArtifactPomName : artifactPoms.keySet()) {
            ArtifactPom activeArtifactPom = artifactPoms.get(activeArtifactPomName);
            File pomFile = createPomFile(activeArtifactPomName);
            MavenPom mavenPom = activeArtifactPom.getPom();
            String packaging = mavenPom.getPackaging();
            MavenProjectIdentity projectIdentity = new ReadableMavenProjectIdentity(mavenPom.getGroupId(), mavenPom.getArtifactId(), mavenPom.getVersion());
            PublishArtifact pomArtifact = activeArtifactPom.writePom(pomFile);
            mavenDeployments.add(new DefaultMavenDeployment(packaging, projectIdentity, pomArtifact, activeArtifactPom.getArtifact(), activeArtifactPom.getAttachedArtifacts()));
        }
        return mavenDeployments;
    }

    private File createPomFile(String artifactPomName) {
        return new File(pomMetaInfoProvider.getMavenPomDir(), "pom-" + artifactPomName + ".xml");
    }
}
