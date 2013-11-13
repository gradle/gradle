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

package org.gradle.buildinit.tasks

import org.gradle.api.GradleException
import org.gradle.buildinit.plugins.internal.BuildInitTypeIds
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.ProjectInitDescriptor
import org.gradle.util.TestUtil
import spock.lang.Specification

class InitBuildSpec extends Specification {

    InitBuild init;

    ProjectLayoutSetupRegistry projectLayoutRegistry;

    ProjectInitDescriptor projectSetupDescriptor1
    ProjectInitDescriptor projectSetupDescriptor2
    ProjectInitDescriptor projectSetupDescriptor3

    def setup() {
        init = TestUtil.createTask(InitBuild)
        projectLayoutRegistry = Mock()
        projectSetupDescriptor1 = Mock()
        projectSetupDescriptor2 = Mock()
        projectSetupDescriptor3 = Mock()
        init.projectLayoutRegistry = projectLayoutRegistry
    }

    def "throws GradleException if requested setupDescriptor not supported"() {
        setup:
        _ * projectLayoutRegistry.get("aType") >> null
        _ * projectLayoutRegistry.getSupportedTypes() >> ['supported-type', 'another-supported-type']
        when:
        init.type = "aType"
        init.setupProjectLayout()
        then:
        def e = thrown(GradleException)
        e.message == "The requested build setup type 'aType' is not supported. Supported types: 'another-supported-type', 'supported-type'."

    }

    def "delegates task action to referenced setupDescriptor"() {
        setup:
        1 * projectLayoutRegistry.supports(BuildInitTypeIds.BASIC) >> true
        1 * projectLayoutRegistry.get(BuildInitTypeIds.BASIC) >> projectSetupDescriptor1
        when:
        init.setupProjectLayout()
        then:
        1 * projectSetupDescriptor1.generate()
    }
}
