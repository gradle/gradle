/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.external.ivypublish

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.text.SimpleDateFormat

class DefaultIvyModuleDescriptorWriterTest extends Specification {

    private @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.test", "projectA"), "1.0")
    ComponentSelectorConverter componentSelectorConverter = Mock(ComponentSelectorConverter)
    def ivyXmlModuleDescriptorWriter = new DefaultIvyModuleDescriptorWriter(componentSelectorConverter)

    def "can create ivy (unmodified) descriptor"() {
        when:
        def metadata = new DefaultIvyModulePublishMetadata(id, "integration")
        addConfiguration(metadata, "archives")
        addConfiguration(metadata, "compile")
        def conf = addConfiguration(metadata, "runtime", ["compile"])
        addDependencyDescriptor(conf, "Dep1")
        addDependencyDescriptor(conf, "Dep2")
        metadata.addArtifact(new DefaultIvyModuleArtifactPublishMetadata(id, new DefaultIvyArtifactName("testartifact", "jar", "jar"), ["archives", "runtime"] as Set))

        1 * componentSelectorConverter.getSelector(_) >> DefaultModuleVersionSelector.newSelector(DefaultModuleIdentifier.newId("org.test", "Dep1"), "1.0")
        1 * componentSelectorConverter.getSelector(_) >> DefaultModuleVersionSelector.newSelector(DefaultModuleIdentifier.newId("org.test", "Dep2"), "1.0")
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

    def addDependencyDescriptor(BuildableLocalConfigurationMetadata metadata, String organisation = "org.test", String moduleName, String revision = "1.0") {
        def dep = new LocalComponentDependencyMetadata(metadata.getComponentId(),
                DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(organisation, moduleName), new DefaultMutableVersionConstraint(revision)),
                "runtime", null, ImmutableAttributes.EMPTY, "default", [] as List, [], false, false, true, false, false, null)
        metadata.addDependency(dep)
    }

    def addConfiguration(DefaultIvyModulePublishMetadata metadata, String configurationName, List extended = []) {
        metadata.addConfiguration(configurationName, extended as Set, true, true)
    }
}
