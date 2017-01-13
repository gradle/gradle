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
import org.gradle.api.file.FileCopyDetails
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import spock.lang.Specification

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class PlayDistributionPluginRenameArtifactFilesTest extends Specification {
    def "only attempts to rename projects and not external dependencies based on component id"() {
        given:
        String projectFileName = 'project.jar'
        File projectFile = new File(projectFileName)
        String newProjectName = "subproject-" + projectFileName
        ResolvedArtifact projectArtifact = resolvedArtifact(projectFile, newProjectId(":subproject"))
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

        def renamer = new PlayDistributionPlugin.PrefixArtifactFileNames(null) {
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
        renameForProject(":sub", "something.jar") == "sub-something.jar"
        renameForProject(":sub", "this.is.a.jar.jar") == "sub-this.is.a.jar.jar"

        renameForProject(":sub", "something.war") == "something.war"
        renameForProject(":sub", "something.txt") == "something.txt"
        renameForProject(":sub", "something.zip") == "something.zip"

        renameForModule("group", "something.jar") == "group-something.jar"
        renameForModule("group", "something.war") == "something.war"
    }

    def "renames projects based on their project path"() {
        expect:
        renameForProject(":", "file.jar") == "file.jar"
        renameForProject(":subproject", "file.jar") == "subproject-file.jar"
        renameForProject(":subproject:deeper", "file.jar") == "subproject.deeper-file.jar"
        renameForProject(":subproject:deeper:deepest", "file.jar") == "subproject.deeper.deepest-file.jar"
    }

    def "renames modules based on their group"() {
        expect:
        renameForModule("", "file.jar") == "file.jar"
        renameForModule("com", "file.jar") == "com-file.jar"
        renameForModule("com.example", "file.jar") == "com.example-file.jar"
    }

    private String renameForProject(String projectPath, String fileName) {
        return PlayDistributionPlugin.PrefixArtifactFileNames.renameForProject(newProjectId(projectPath), new File(fileName))
    }

    private String renameForModule(String groupName, String fileName) {
        return PlayDistributionPlugin.PrefixArtifactFileNames.renameForModule(moduleId(groupName), new File(fileName))
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
