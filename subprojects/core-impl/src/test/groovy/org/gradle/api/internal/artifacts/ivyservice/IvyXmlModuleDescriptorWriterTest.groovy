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

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

import java.text.SimpleDateFormat

class IvyXmlModuleDescriptorWriterTest extends Specification {

    private @Rule TemporaryFolder temporaryFolder;
    private ModuleDescriptor md = Mock();
    private ModuleRevisionId moduleRevisionId = Mock()
    private ModuleRevisionId resolvedModuleRevisionId = Mock()
    private PrintWriter printWriter = Mock()
    def ivyXmlModuleDescriptorWriter = new IvyXmlModuleDescriptorWriter()

    def setup() {
        1 * md.extraAttributesNamespaces >> [:]
        1 * md.extraAttributes >> [buildNr: "815"]
        1 * md.qualifiedExtraAttributes >> [buildNr: "815"]
        1 * md.getExtraInfo() >> Collections.emptyMap()
        1 * md.getLicenses() >> Collections.emptyList()
        2 * md.inheritedDescriptors >> Collections.emptyList()
        1 * md.resolvedModuleRevisionId >> resolvedModuleRevisionId
        1 * md.resolvedPublicationDate >> date("20120817120000")
        1 * md.status >> "integration"
        1 * resolvedModuleRevisionId.revision >> "1.0"
        1 * md.moduleRevisionId >> moduleRevisionId;
        1 * moduleRevisionId.organisation >> "org.test"
        1 * moduleRevisionId.name >> "projectA"
        1 * md.configurations >> [mockConfiguration("archives"), mockConfiguration("compile"), mockConfiguration("runtime", ["compile"])]
        1 * md.configurationsNames >> ["archives", "runtime", "compile"]
        1 * md.allExcludeRules >> []
        1 * md.allArtifacts >> [mockArtifact()]
        1 * md.allDependencyDescriptorMediators >> []
    }

    def "can create ivy (unmodified) descriptor"() {
        setup:
        def dependency1 = mockDependencyDescriptor("Dep1")
        def dependency2 = mockDependencyDescriptor("Dep2")
        2 * dependency2.force >> true
        2 * dependency2.changing >> true
        1 * md.dependencies >> [dependency1, dependency2]
        1 * dependency1.transitive >> true
        when:
        File ivyFile = temporaryFolder.file("test/ivy/ivy.xml")
        ivyXmlModuleDescriptorWriter.write(md, ivyFile);
        then:
        def ivyModule = new XmlSlurper().parse(ivyFile);
        assert ivyModule.@version == "2.0"
        assert ivyModule.info.@organisation == "org.test"
        assert ivyModule.info.@module == "projectA"
        assert ivyModule.info.@revision == "1.0"
        assert ivyModule.info.@status == "integration"
        assert ivyModule.info.@publication == "20120817120000"
        assert ivyModule.info.@buildNr == "815"
        assert ivyModule.configurations.conf.collect {it.@name } == ["archives", "compile", "runtime"]
        assert ivyModule.publications.artifact.collect {it.@name } == ["testartifact"]
        assert ivyModule.dependencies.dependency.collect { "${it.@org}:${it.@name}:${it.@rev}" } == ["org.test:Dep1:1.0", "org.test:Dep2:1.0"]
    }

    def "can create ivy (modified) descriptor"() {
        setup:
        def dependency1 = mockDependencyDescriptor("Dep1")
        def dependency2 = mockDependencyDescriptor("Dep2")
        1 * md.dependencies >> [dependency1, dependency2]
        when:
        File ivyFile = temporaryFolder.file("test/ivy/ivy.xml")
        XmlTransformer xmlTransformer = new XmlTransformer()
        xmlTransformer.addAction(new Action<XmlProvider>() {
            void execute(XmlProvider xml) {
                def node = xml.asNode()
                node.info[0].@status = "foo"
                assert node.dependencies.dependency.collect { "${it.@org}:${it.@name}:${it.@rev}" } == ["org.test:Dep1:1.0", "org.test:Dep2:1.0"]
                node.dependencies[0].dependency[0].@name = "changed"
            }
        })
        ivyXmlModuleDescriptorWriter.write(md, ivyFile, xmlTransformer)

        then:
        def ivyModule = new XmlSlurper().parse(ivyFile);
        ivyModule.info.@status == "foo"
        ivyModule.dependencies.dependency.collect { "${it.@org}:${it.@name}:${it.@rev}" } == ["org.test:changed:1.0", "org.test:Dep2:1.0"]
    }

    def date(String timestamp) {
        def format = new SimpleDateFormat("yyyyMMddHHmmss")
        format.parse(timestamp)
    }

    def mockDependencyDescriptor(String organisation = "org.test", String moduleName, String revision = "1.0") {
        DependencyDescriptor dependencyDescriptor = Mock()
        ModuleRevisionId moduleRevisionId = Mock()
        1 * moduleRevisionId.organisation >> organisation
        1 * moduleRevisionId.name >> moduleName
        1 * moduleRevisionId.revision >> revision
        1 * dependencyDescriptor.dependencyRevisionId >> moduleRevisionId
        1 * dependencyDescriptor.dynamicConstraintDependencyRevisionId >> moduleRevisionId
        1 * dependencyDescriptor.moduleConfigurations >> ["default"]
        1 * dependencyDescriptor.getDependencyConfigurations("default") >> ["compile, archives"]
        1 * dependencyDescriptor.allDependencyArtifacts >> []
        1 * dependencyDescriptor.allIncludeRules >> []
        1 * dependencyDescriptor.allExcludeRules >> []
        dependencyDescriptor
    }

    def mockArtifact() {
        Artifact artifact = Mock()
        1 * artifact.name >> "testartifact"
        1 * artifact.ext >> "jar"
        1 * artifact.type >> "jar"
        1 * md.getArtifacts("archives") >> [artifact]
        1 * md.getArtifacts("compile") >> []
        1 * md.getArtifacts("runtime") >> []
        artifact
    }

    def mockConfiguration(String configurationName, List extended = []) {
        Configuration configuration = Mock()
        1 * configuration.name >> configurationName
        1 * configuration.description >> "just another test configuration"
        1 * configuration.extends >> extended
        1 * configuration.visibility >> Configuration.Visibility.PUBLIC
        configuration
    }
}
