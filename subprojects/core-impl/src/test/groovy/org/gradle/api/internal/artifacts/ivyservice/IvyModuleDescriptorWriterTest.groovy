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
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification

/**
 * Created with IntelliJ IDEA.
 * User: Rene
 * Date: 12.09.12
 * Time: 17:49
 * To change this template use File | Settings | File Templates.
 */
class IvyModuleDescriptorWriterTest extends Specification {


    private @Rule TemporaryFolder temporaryFolder;
    private ModuleDescriptor md = Mock();
    private ModuleRevisionId moduleRevisionId = Mock()
    private ModuleRevisionId resolvedModuleRevisionId = Mock()

    def "can create empty ivy descriptor"() {
        given:
        _ * md.extraAttributesNamespaces >> Collections.emptyMap()
        _ * md.extraAttributes >> Collections.emptyMap()
        _ * md.extraAttributes >> Collections.emptyMap()
        _ * md.getExtraInfo() >> Collections.emptyMap()
        _ * md.getLicenses() >> Collections.emptyList()
        _ * md.inheritedDescriptors >> Collections.emptyList()
        _ * md.resolvedModuleRevisionId >> resolvedModuleRevisionId
        _ * md.resolvedPublicationDate >> new Date(0)
        _ * md.moduleRevisionId >> moduleRevisionId;
        _ * moduleRevisionId.organisation >> "org.test"
        _ * moduleRevisionId.name >> "projectA"
        _ * md.getConfigurations() >> new Configuration[0]
        _ * md.getAllArtifacts() >> new Artifact[0]
        _ * md.getDependencies() >> new DependencyDescriptor[0]


        when:

        File ivyFile = temporaryFolder.file("ivy.xml")
        IvyModuleDescriptorWriter.write(md, ivyFile);
        then:
        isIvyFile(ivyFile)
    }

    void isIvyFile(TestFile testFile) {
        println testFile.text
        def ivyModule = new XmlSlurper().parse(testFile);
        assert ivyModule.@version == "2.0"
        assert ivyModule.info.@organisation == "org.test"
        assert ivyModule.info.@module == "projectA"
        assert ivyModule.info.@publication == "19700101010000"
    }
}
