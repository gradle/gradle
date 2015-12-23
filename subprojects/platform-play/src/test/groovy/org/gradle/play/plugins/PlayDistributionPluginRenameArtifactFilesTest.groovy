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
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCopyDetails
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import spock.lang.Specification

class PlayDistributionPluginRenameArtifactFilesTest extends Specification {
    def "only attempts to rename projects and not external dependencies based on component id"() {
        given:
        String projectFileName = 'project.jar'
        File projectFile = new File(projectFileName)
        String newProjectName = "subproject-" + projectFileName
        ResolvedArtifact projectArtifact = resolvedArtifact(projectFile, projectId(":subproject"))
        FileCopyDetails projectFcd = Mock()
        projectFcd.getFile() >> projectFile

        String moduleFileName = 'module.jar'
        File moduleFile = new File(moduleFileName)
        String newModuleName = "com.example-" + moduleFileName
        ResolvedArtifact moduleArtifact = resolvedArtifact(moduleFile, moduleId("com.example"))
        FileCopyDetails moduleFcd = Mock()
        moduleFcd.getFile() >> moduleFile

        String nonProjectFileName = 'dependency.jar'
        File nonProjectFile = new File(nonProjectFileName)
        ResolvedArtifact nonProjectArtifact = resolvedArtifact(nonProjectFile, new OpaqueComponentIdentifier("non-project"))
        FileCopyDetails nonProjectFcd = Mock()
        nonProjectFcd.getFile() >> nonProjectFile

        def renamer = new PlayDistributionPlugin.RenameArtifactFiles(null) {
            Set<ResolvedArtifact> getResolvedArtifacts() {
                return [projectArtifact, moduleArtifact, nonProjectArtifact] as Set
            }
        }

        String unknownFileName = 'unknown.jar'
        File unknownFile = new File(unknownFileName)
        FileCopyDetails unknownFcd = Mock()
        unknownFcd.getFile() >> unknownFile

        when:
        renamer.execute(projectFcd)
        then:
        1 * projectFcd.setName(newProjectName)
        and:
        renamer.apply(projectFile) == newProjectName

        when:
        renamer.execute(moduleFcd)
        then:
        1 * moduleFcd.setName(newModuleName)
        and:
        renamer.apply(moduleFile) == newModuleName

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
        PlayDistributionPlugin.RenameArtifactFiles.shouldBeRenamed(new File("something.jar"))
        PlayDistributionPlugin.RenameArtifactFiles.shouldBeRenamed(new File("this.is.a.jar.jar"))
        !PlayDistributionPlugin.RenameArtifactFiles.shouldBeRenamed(new File("something.war"))
        !PlayDistributionPlugin.RenameArtifactFiles.shouldBeRenamed(new File("something.txt"))
        !PlayDistributionPlugin.RenameArtifactFiles.shouldBeRenamed(new File("something.zip"))
    }

    def "turns project path into friendly file name"() {
        expect:
        PlayDistributionPlugin.RenameArtifactFiles.projectPathToSafeFileName(":") == "root"
        PlayDistributionPlugin.RenameArtifactFiles.projectPathToSafeFileName(":subproject") == "subproject"
        PlayDistributionPlugin.RenameArtifactFiles.projectPathToSafeFileName(":subproject:deeper") == "subproject.deeper"
        PlayDistributionPlugin.RenameArtifactFiles.projectPathToSafeFileName(":subproject:deeper:deepest") == "subproject.deeper.deepest"
    }

    def "renames projects based on their project path"() {
        given:
        def file = new File("file.jar")
        expect:
        PlayDistributionPlugin.RenameArtifactFiles.renameForProject(projectId(":"), file) == "root-file.jar"
        PlayDistributionPlugin.RenameArtifactFiles.renameForProject(projectId(":subproject"), file) == "subproject-file.jar"
        PlayDistributionPlugin.RenameArtifactFiles.renameForProject(projectId(":subproject:deeper"), file) == "subproject.deeper-file.jar"
        PlayDistributionPlugin.RenameArtifactFiles.renameForProject(projectId(":subproject:deeper:deepest"), file) == "subproject.deeper.deepest-file.jar"
    }

    def "renames modules based on their group"() {
        given:
        def file = new File("file.jar")
        expect:
        PlayDistributionPlugin.RenameArtifactFiles.renameForModule(moduleId(""), file) == "file.jar"
        PlayDistributionPlugin.RenameArtifactFiles.renameForModule(moduleId("com"), file) == "com-file.jar"
        PlayDistributionPlugin.RenameArtifactFiles.renameForModule(moduleId("com.example"), file) == "com.example-file.jar"
    }

    private ProjectComponentIdentifier projectId(String path) {
        return new DefaultProjectComponentIdentifier(path)
    }

    private ModuleComponentIdentifier moduleId(String group) {
        return new DefaultModuleComponentIdentifier(group, "module", "1.0")
    }

    private ResolvedArtifact resolvedArtifact(File artifactFile, ComponentIdentifier componentId) {
        ResolvedArtifact artifact = Mock()
        artifact.getFile() >> artifactFile
        ComponentArtifactIdentifier componentArtifactId = Mock()
        artifact.getId() >> componentArtifactId
        componentArtifactId.getComponentIdentifier() >> componentId
        return artifact
    }
}
