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

package org.gradle.integtests.resolve.ivy

import groovy.xml.XmlUtil;
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest;

public class DisableIvyDescriptorValidationIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "does not try to validate ivy descriptor"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
        validateDescriptors false
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task showFiles << { println configurations.compile.files }
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').artifact().publish()
        def moduleDescriptorXml = new XmlSlurper().parse(module.ivyFile)
        moduleDescriptorXml.info.'@invalid-attribute' = 'foo'
        module.ivyFile.text = XmlUtil.serialize(moduleDescriptorXml)

        when:
        module.ivy.expectGet()
        module.artifact.expectGet()

        then:
        succeeds "showFiles"
    }
}
