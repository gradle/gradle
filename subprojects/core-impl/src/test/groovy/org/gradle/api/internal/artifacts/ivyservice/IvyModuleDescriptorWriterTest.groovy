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
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

import java.text.SimpleDateFormat

class IvyModuleDescriptorWriterTest extends Specification {

    private @Rule TemporaryFolder temporaryFolder;
    private ModuleDescriptor md = Mock();
    private ModuleRevisionId moduleRevisionId = Mock()
    private ModuleRevisionId resolvedModuleRevisionId = Mock()
    private PrintWriter printWriter = Mock()

    def setup() {
        _ * md.extraAttributesNamespaces >> [:]
        _ * md.extraAttributes >> [buildNr: "815"]
        _ * md.qualifiedExtraAttributes >> [buildNr: "815"]
        _ * md.getExtraInfo() >> Collections.emptyMap()
        _ * md.getLicenses() >> Collections.emptyList()
        _ * md.inheritedDescriptors >> Collections.emptyList()
        _ * md.resolvedModuleRevisionId >> resolvedModuleRevisionId
        _ * md.resolvedPublicationDate >> date("20120817120000")
        _ * md.status >> "integration"
        _ * resolvedModuleRevisionId.revision >> "1.0"
        _ * md.moduleRevisionId >> moduleRevisionId;
        _ * moduleRevisionId.organisation >> "org.test"
        _ * moduleRevisionId.name >> "projectA"
        _ * md.configurations >> [mockConfiguration("archives"), mockConfiguration("compile"), mockConfiguration("runtime", ["compile"])]
        _ * md.configurationsNames >> ["archives", "runtime", "compile"]
        _ * md.allExcludeRules >> []
        _ * md.allArtifacts >> [mockArtifact()]
        _ * md.allDependencyDescriptorMediators >> []
        _ * md.publicConfigurationsNames >> ["archives"]
    }


    def "can create ivy descriptor"() {
        setup:
        def dependency1 = mockDependencyDescriptor("Dep1")
        def dependency2 = mockDependencyDescriptor("Dep2")
        _ * dependency1.transitive >> true
        _ * dependency2.force >> true
        _ * dependency2.changing >> true
        _ * md.dependencies >> [dependency1, dependency2]
        when:
        File ivyFile = temporaryFolder.file("test/ivy/ivy.xml")
        IvyModuleDescriptorWriter.write(md, ivyFile);
        then:
        def ivyModule = new XmlSlurper().parse(ivyFile);
        assert ivyModule.@version == "2.0"
        assert ivyModule.info.@organisation == "org.test"
        assert ivyModule.info.@module == "projectA"
        assert ivyModule.info.@revision == "1.0"
        assert ivyModule.info.@status == "integration"
        assert ivyModule.info.@publication == "20120817120000"
        assert ivyModule.info.@buildNr == "815"
        assert ivyModule.configurations.conf.collect{it.@name } == ["archives", "compile", "runtime"]
        assert ivyModule.publications.artifact.collect{it.@name } == ["testartifact"]
        assert ivyModule.dependencies.dependency.collect{ "${it.@org}:${it.@name}:${it.@rev}" } == ["org.test:Dep1:1.0", "org.test:Dep2:1.0"]
    }

    def "error in printwriter are escalated as IOException"() {
        given:
        _ * md.dependencies >> [mockDependencyDescriptor("Dep1"), mockDependencyDescriptor("Dep2")]

        printWriter.checkError() >> true
        when:
        IvyModuleDescriptorWriter.write(md, printWriter);
        then:
        thrown(IOException)
    }

    def date(String timestamp) {
        def format = new SimpleDateFormat("yyyyMMddHHmmss")
        format.parse(timestamp)
    }

    def mockDependencyDescriptor(String organisation = "org.test", String moduleName, String revision = "1.0") {
        DependencyDescriptor dependencyDescriptor = Mock()
        ModuleRevisionId moduleRevisionId = Mock()
        _ * moduleRevisionId.organisation >> organisation
        _ * moduleRevisionId.name >> moduleName
        _ * moduleRevisionId.revision >> revision
        _ * dependencyDescriptor.dependencyRevisionId >> moduleRevisionId
        _ * dependencyDescriptor.dynamicConstraintDependencyRevisionId >> moduleRevisionId
        _ * dependencyDescriptor.moduleConfigurations >> ["default"]
        _ * dependencyDescriptor.getDependencyConfigurations("default") >> ["compile, archives"]
        _ * dependencyDescriptor.getDependencyConfigurations("compile") >> ["transitive"]
        _ * dependencyDescriptor.allDependencyArtifacts >> []
        _ * dependencyDescriptor.allIncludeRules >> []
        _ * dependencyDescriptor.allExcludeRules >> []
        dependencyDescriptor
    }

    def mockArtifact() {
        Artifact artifact = Mock()
        _ * artifact.name >> "testartifact"
        _ * artifact.configurations >> ["archives, runtime"]
        _ * artifact.ext >> "jar"
        _ * artifact.type >> "jar"
        _ * md.getArtifacts("archives") >> [artifact]
        _ * md.getArtifacts("compile") >> []
        _ * md.getArtifacts("runtime") >> []
        artifact
    }

    def mockConfiguration(String configurationName, List extended = []) {
        Configuration configuration = Mock()
        _ * configuration.name >> configurationName
        _ * configuration.description >> "just another test configuration"
        _ * configuration.extends >> extended
        _ * configuration.visibility >> Configuration.Visibility.PUBLIC
        configuration
    }
}
