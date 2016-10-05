/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.publisher.IvyXmlModuleDescriptorWriter
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetadata
import org.gradle.internal.component.external.model.DefaultIvyModuleArtifactPublishMetadata
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.text.SimpleDateFormat

class IvyXmlModuleDescriptorWriterTest extends Specification {

    private @Rule TestNameTestDirectoryProvider temporaryFolder;
    ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId("org.test", "projectA", "1.0")
    def ivyXmlModuleDescriptorWriter = new IvyXmlModuleDescriptorWriter()

    def "can create ivy (unmodified) descriptor"() {
        when:
        def descriptor = new MutableModuleDescriptorState(id)
        def metadata = new DefaultIvyModulePublishMetadata(id, descriptor)
        addConfiguration(metadata, "archives")
        addConfiguration(metadata, "compile")
        addConfiguration(metadata, "runtime", ["compile"])
        addDependencyDescriptor(metadata, "Dep1")
        addDependencyDescriptor(metadata, "Dep2")
        metadata.addArtifact(new DefaultIvyModuleArtifactPublishMetadata(id, DefaultIvyArtifactName.of("testartifact", "jar", "jar"), ["archives", "runtime"] as Set))

        File ivyFile = temporaryFolder.file("test/ivy/ivy.xml")
        ivyXmlModuleDescriptorWriter.write(metadata, ivyFile);

        then:
        def ivyModule = new XmlSlurper().parse(ivyFile);
        assert ivyModule.@version == "2.0"
        assert ivyModule.info.@organisation == "org.test"
        assert ivyModule.info.@module == "projectA"
        assert ivyModule.info.@revision == "1.0"
        assert ivyModule.info.@status == "integration"
        assert ivyModule.configurations.conf.collect {it.@name } == ["archives", "compile", "runtime"]
        assert ivyModule.publications.artifact.collect {it.@name } == ["testartifact"]
        assert ivyModule.publications.artifact.collect {it.@conf } == ["archives,runtime"]
        assert ivyModule.dependencies.dependency.collect { "${it.@org}:${it.@name}:${it.@rev}" } == ["org.test:Dep1:1.0", "org.test:Dep2:1.0"]
    }

    def date(String timestamp) {
        def format = new SimpleDateFormat("yyyyMMddHHmmss")
        format.parse(timestamp)
    }

    def addDependencyDescriptor(BuildableIvyModulePublishMetadata metadata, String organisation = "org.test", String moduleName, String revision = "1.0") {
        def dep = new LocalComponentDependencyMetadata(
            DefaultModuleComponentSelector.newSelector(organisation, moduleName, revision),
            DefaultModuleVersionSelector.newSelector(organisation, moduleName, revision),
            "default", null, "default", [] as Set, [], false, false, true)
        metadata.addDependency(dep)
    }

    def addConfiguration(BuildableIvyModulePublishMetadata metadata, String configurationName, List extended = []) {
        metadata.addConfiguration(configurationName, null, extended as Set, extended as Set, true, true, null, null)
    }
}
