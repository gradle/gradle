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

package org.gradle.play.plugins

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCopyDetails
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import spock.lang.Specification

class PlayConfgurationRenamerTest extends Specification {
    def "only attempts to rename projects and not external dependencies based on component id"() {
        given:
        def projectFile = new File("project.jar")
        def projectFileName = projectFile.getName()
        def newName = "subproject-" + projectFileName
        ResolvedArtifact projectArtifact = Mock()
        projectArtifact.getFile() >> projectFile
        ComponentArtifactIdentifier projectComponentId = Mock()
        projectArtifact.getId() >> projectComponentId
        projectComponentId.getComponentIdentifier() >> projectId(":subproject")

        def nonProjectFile = new File("dependency.jar")
        def nonProjectFileName = nonProjectFile.getName()
        ResolvedArtifact nonProjectArtifact = Mock()
        nonProjectArtifact.getFile() >> nonProjectFile
        ComponentArtifactIdentifier nonProjectComponentId = Mock()
        nonProjectArtifact.getId() >> nonProjectComponentId
        nonProjectComponentId.getComponentIdentifier() >> new OpaqueComponentIdentifier("non-project")

        def renamer = new PlayPluginConfigurations.Renamer(Mock(PlayPluginConfigurations.PlayConfiguration)) {
            Set<ResolvedArtifact> getResolvedArtifacts() {
                return [projectArtifact, nonProjectArtifact] as Set
            }
        }
        FileCopyDetails projectFcd = Mock()
        projectFcd.getFile() >> projectFile

        FileCopyDetails nonProjectFcd = Mock()
        nonProjectFcd.getFile() >> nonProjectFile

        def unknownFile = new File("unknown.jar")
        def unknownFileName = unknownFile.getName()
        FileCopyDetails unknownFcd = Mock()
        unknownFcd.getFile() >> unknownFile

        when:
        renamer.execute(projectFcd)
        then:
        1 * projectFcd.setName(newName)
        and:
        renamer.apply(projectFile) == newName

        when:
        renamer.execute(nonProjectFcd)
        then:
        1 * nonProjectFcd.setName(nonProjectFileName)
        and:
        renamer.apply(nonProjectFile) == nonProjectFileName

        when:
        renamer.execute(unknownFcd)
        then:
        1 * unknownFcd.setName(unknownFileName)
        and:
        renamer.apply(unknownFile) == unknownFileName
    }

    def "only rename jar files"() {
        expect:
        PlayPluginConfigurations.Renamer.shouldBeRenamed(new File("something.jar"))
        PlayPluginConfigurations.Renamer.shouldBeRenamed(new File("this.is.a.jar.jar"))
        !PlayPluginConfigurations.Renamer.shouldBeRenamed(new File("something.war"))
        !PlayPluginConfigurations.Renamer.shouldBeRenamed(new File("something.txt"))
        !PlayPluginConfigurations.Renamer.shouldBeRenamed(new File("something.zip"))
    }

    def "turns project path into friendly file name"() {
        expect:
        PlayPluginConfigurations.Renamer.projectPathToSafeFileName(":") == "root"
        PlayPluginConfigurations.Renamer.projectPathToSafeFileName(":subproject") == "subproject"
        PlayPluginConfigurations.Renamer.projectPathToSafeFileName(":subproject:deeper") == "subproject.deeper"
        PlayPluginConfigurations.Renamer.projectPathToSafeFileName(":subproject:deeper:deepest") == "subproject.deeper.deepest"
    }

    def "renames projects based on their project path"() {
        given:
        def file = new File("file.jar")
        expect:
        PlayPluginConfigurations.Renamer.rename(projectId(":"), file) == "root-file.jar"
        PlayPluginConfigurations.Renamer.rename(projectId(":subproject"), file) == "subproject-file.jar"
        PlayPluginConfigurations.Renamer.rename(projectId(":subproject:deeper"), file) == "subproject.deeper-file.jar"
        PlayPluginConfigurations.Renamer.rename(projectId(":subproject:deeper:deepest"), file) == "subproject.deeper.deepest-file.jar"
    }

    private ProjectComponentIdentifier projectId(String path) {
        return new DefaultProjectComponentIdentifier(path)
    }
}
