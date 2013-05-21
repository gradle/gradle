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

package org.gradle.buildsetup.tasks

import org.gradle.api.GradleException
import org.gradle.buildsetup.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildsetup.plugins.internal.ProjectSetupDescriptor
import org.gradle.util.HelperUtil
import spock.lang.Specification

class SetupBuildSpec extends Specification {

    SetupBuild setupBuild;

    ProjectLayoutSetupRegistry projectLayoutRegistry;

    ProjectSetupDescriptor projectSetupDescriptor1
    ProjectSetupDescriptor projectSetupDescriptor2
    ProjectSetupDescriptor projectSetupDescriptor3

    def setup() {
        setupBuild = HelperUtil.builder().build().tasks.create("setupBuild", SetupBuild)
        projectLayoutRegistry = Mock()
        projectSetupDescriptor1 = Mock()
        projectSetupDescriptor2 = Mock()
        projectSetupDescriptor3 = Mock()
        _ * projectSetupDescriptor2.id >> "supported-type"
        _ * projectSetupDescriptor3.id >> "another-supported-type"
        setupBuild.projectLayoutRegistry = projectLayoutRegistry
    }

    def "throws GradleException if requested setupDescriptor not supported"() {
        setup:
        _ * projectLayoutRegistry.get("aType") >> null
        _ * projectLayoutRegistry.all >> [projectSetupDescriptor2, projectSetupDescriptor3]
        when:
        setupBuild.type = "aType"
        setupBuild.setupProjectLayout()
        then:
        def e = thrown(GradleException)
        e.message == "The requested build setup type 'aType' is not supported. Supported types: 'supported-type', 'another-supported-type'."

    }

    def "delegates task action to referenced setupDescriptor"() {
        setup:
        1 * projectLayoutRegistry.supports("empty") >> true
        1 * projectLayoutRegistry.get("empty") >> projectSetupDescriptor1
        when:
        setupBuild.setupProjectLayout()
        then:
        1 * projectSetupDescriptor1.generateProject()
    }
}
