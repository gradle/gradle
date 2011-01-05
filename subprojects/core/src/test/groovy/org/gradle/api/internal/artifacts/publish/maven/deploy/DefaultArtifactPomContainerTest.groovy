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
package org.gradle.api.internal.artifacts.publish.maven.deploy

import spock.lang.Specification
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider
import org.gradle.api.artifacts.maven.PomFilterContainer
import org.apache.ivy.core.module.descriptor.Artifact
import org.gradle.api.artifacts.maven.PublishFilter
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.artifacts.PublishArtifact

class DefaultArtifactPomContainerTest extends Specification {
    final MavenPomMetaInfoProvider pomMetaInfoProvider = Mock()
    final PomFilterContainer pomFilterContainer = Mock()
    final ArtifactPomFactory artifactPomFactory = Mock()
    final File pomDir = new File('pomDir')
    final DefaultArtifactPomContainer container = new DefaultArtifactPomContainer(pomMetaInfoProvider, pomFilterContainer, artifactPomFactory)

    def setup() {
        _ * pomMetaInfoProvider.mavenPomDir >> pomDir
    }
    
    def addsArtifactToFirstMatchingArtifactPom() {
        File artifactFile = new File('artifact')
        Artifact artifact = artifact()
        MavenPom templatePom = Mock()
        PomFilter filter1 = alwaysFilter('filterName', templatePom)
        PomFilter filter2 = neverFilter()
        ArtifactPom pom = pom('artifactId')
        PublishArtifact pomArtifact = Mock()
        PublishArtifact mainArtifact = Mock()
        PublishArtifact attachArtifact = Mock()

        when:
        container.addArtifact(artifact, artifactFile)
        def infos = container.createDeployableFilesInfos()

        then:
        _ * pomFilterContainer.activePomFilters >> [filter1, filter2]
        _ * artifactPomFactory.createArtifactPom(templatePom) >> pom
        _ * pom.writePom(new File(pomDir, 'pom-filterName.xml')) >> pomArtifact
        _ * pom.artifact >> mainArtifact
        _ * pom.attachedArtifacts >> ([attachArtifact] as Set)

        infos.size() == 1
        DefaultMavenDeployment info = infos.asList()[0]

        info.pomArtifact == pomArtifact
        info.mainArtifact == mainArtifact
        info.artifacts == [pomArtifact, mainArtifact, attachArtifact] as Set
        info.attachedArtifacts == [attachArtifact] as Set
    }

    def alwaysFilter(String filterName, MavenPom template) {
        filter(filterName, true, template)
    }

    def neverFilter() {
        filter('never', false)
    }

    def filter(String name, boolean accept, MavenPom template = null) {
        PomFilter filter = Mock()
        PublishFilter publishFilter = Mock()
        _ * filter.name >> name
        _ * filter.filter >> publishFilter
        _ * publishFilter.accept(_, _) >> accept
        _ * filter.pomTemplate >> template
        filter
    }

    def artifact() {
        Artifact artifact = Mock()
        return artifact
    }

    def pom(String artifactId) {
        ArtifactPom pom = Mock()
        MavenPom mavenPom = Mock()
        _ * pom.pom >> mavenPom
        _ * mavenPom.artifactId >> artifactId
        return pom
    }
}
