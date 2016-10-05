/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.util.WrapUtil
import spock.lang.Specification

class DefaultLocalComponentMetadataTest extends Specification {
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "version")
    def componentIdentifier = DefaultModuleComponentIdentifier.newId(id)
    def metadata = new DefaultLocalComponentMetadata(id, componentIdentifier, "status")
    def taskDep = new DefaultTaskDependency()

    def "can lookup configuration after it has been added"() {
        when:
        metadata.addConfiguration("super", "description", [] as Set, ["super"] as Set, false, false, null, taskDep)
        metadata.addConfiguration("conf", "description", ["super"] as Set, ["super", "conf"] as Set, true, true, null, taskDep)

        then:
        metadata.configurationNames == ['conf', 'super'] as Set

        def conf = metadata.getConfiguration('conf')
        conf != null
        conf.visible
        conf.transitive

        def superConf = metadata.getConfiguration('super')
        superConf != null
        !superConf.visible
        !superConf.transitive
    }

    def "can lookup artifact in various ways after it has been added"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        addConfiguration("conf")

        when:
        addArtifact("conf", artifact, file)

        then:
        metadata.getConfiguration("conf").artifacts.size() == 1

        def publishArtifact = metadata.getConfiguration("conf").artifacts.first()
        publishArtifact.id
        publishArtifact.name.name == artifact.name
        publishArtifact.name.type == artifact.type
        publishArtifact.name.extension == artifact.extension
        publishArtifact.file == file
        publishArtifact == metadata.getConfiguration("conf").artifact(artifact)
    }

    private addConfiguration(String name) {
        metadata.addConfiguration(name, "", [] as Set, [name] as Set, true, true, null, taskDep)
    }

    def addArtifact(String configuration, IvyArtifactName name, File file) {
        PublishArtifact publishArtifact = new DefaultPublishArtifact(name.name, name.extension, name.type, name.classifier, new Date(), file)
        addArtifact(configuration, publishArtifact)
    }

    def addArtifact(String configuration, PublishArtifact publishArtifact) {
        metadata.addArtifacts(configuration, new DefaultPublishArtifactSet("arts", WrapUtil.toDomainObjectSet(PublishArtifact, publishArtifact), TestFiles.fileCollectionFactory()))
    }

    def "can add artifact to several configurations"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        addConfiguration("conf1")
        addConfiguration("conf2")

        when:
        def publishArtifact = new DefaultPublishArtifact(artifact.name, artifact.extension, artifact.type, artifact.classifier, new Date(), file)
        addArtifact("conf1", publishArtifact)
        addArtifact("conf2", publishArtifact)

        then:
        metadata.getConfiguration("conf1").artifacts.size() == 1
        metadata.getConfiguration("conf1").artifacts == metadata.getConfiguration("conf2").artifacts
    }

    def "can lookup an artifact given an Ivy artifact"() {
        def artifact = artifactName()
        def file = new File("artifact.zip")

        given:
        addConfiguration("conf")

        and:
        addArtifact("conf", artifact, file)

        and:
        def ivyArtifact = artifactName()

        expect:
        def resolveArtifact = metadata.getConfiguration("conf").artifact(ivyArtifact)
        resolveArtifact.file == file
    }

    def "can lookup an unknown artifact given an Ivy artifact"() {
        def artifact = artifactName()
        given:
        addConfiguration("conf")

        expect:
        def resolveArtifact = metadata.getConfiguration("conf").artifact(artifact)
        resolveArtifact != null
        resolveArtifact.file == null
    }

    def "treats as distinct two artifacts with duplicate attributes and different files"() {
        def artifact1 = artifactName()
        def artifact2 = artifactName()
        def file1 = new File("artifact-1.zip")
        def file2 = new File("artifact-2.zip")

        when:
        addConfiguration("conf1")
        addConfiguration("conf2")
        addArtifact("conf1", artifact1, file1)
        addArtifact("conf2", artifact2, file2)

        then:
        def conf1Artifacts = metadata.getConfiguration("conf1").artifacts as List
        conf1Artifacts.size() == 1
        def artifactMetadata1 = conf1Artifacts[0]

        def conf2Artifacts = metadata.getConfiguration("conf2").artifacts as List
        conf2Artifacts.size() == 1
        def artifactMetadata2 = conf2Artifacts[0]

        and:
        artifactMetadata1.id != artifactMetadata2.id

        and:
        metadata.getConfiguration("conf1").artifacts == [artifactMetadata1] as Set
        metadata.getConfiguration("conf2").artifacts == [artifactMetadata2] as Set
    }

    def "can add dependencies"() {
        def dependency = Mock(LocalOriginDependencyMetadata)

        when:
        metadata.addDependency(dependency)

        then:
        metadata.dependencies == [dependency]
    }

    def "dependency is attached to module configuration and its children"() {
        def dependency1 = Mock(LocalOriginDependencyMetadata)
        dependency1.moduleConfiguration >> "conf"
        def dependency2 = Mock(LocalOriginDependencyMetadata)
        dependency2.moduleConfiguration >> "child"

        when:
        metadata.addConfiguration("conf", null, [] as Set, ["conf"] as Set, true, true, null, Stub(TaskDependency))
        metadata.addConfiguration("child", null, ["conf"] as Set, ["conf", "child"] as Set, true, true, null, Stub(TaskDependency))
        metadata.addConfiguration("other", null, [] as Set, ["other"] as Set, true, true, null, Stub(TaskDependency))
        metadata.addDependency(dependency1)
        metadata.addDependency(dependency2)

        then:
        metadata.getConfiguration("conf").dependencies == [dependency1]
        metadata.getConfiguration("child").dependencies == [dependency1, dependency2]
        metadata.getConfiguration("other").dependencies.isEmpty()
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        metadata.addConfiguration("compile", null, [] as Set, ["compile"] as Set, true, true, null, Stub(TaskDependency))
        metadata.addConfiguration("runtime", null, ["compile"] as Set, ["compile", "runtime"] as Set, true, true, null, Stub(TaskDependency))

        def rule1 = new DefaultExclude("group1", "module1", ["compile"] as String[], PatternMatchers.EXACT)
        def rule2 = new DefaultExclude("group1", "module1", ["runtime"] as String[], PatternMatchers.EXACT)
        def rule3 = new DefaultExclude("group1", "module1", ["other"] as String[], PatternMatchers.EXACT)

        metadata.addExclude(rule1)
        metadata.addExclude(rule2)
        metadata.addExclude(rule3)

        expect:
        def config = metadata.getConfiguration("runtime")

        def exclusions = config.exclusions
        exclusions == ModuleExclusions.excludeAny(rule1, rule2)
        exclusions.is(config.exclusions)
    }

    def artifactName() {
        return DefaultIvyArtifactName.of("artifact", "type", "ext")
    }
}
