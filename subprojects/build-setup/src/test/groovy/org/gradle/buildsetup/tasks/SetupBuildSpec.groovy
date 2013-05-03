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

    ProjectSetupDescriptor projectSetupDescriptor

    def setup() {
        setupBuild = HelperUtil.builder().build().tasks.create("setupBuild", SetupBuild)
        projectLayoutRegistry = Mock()
        projectSetupDescriptor = Mock()
        setupBuild.projectLayoutRegistry = projectLayoutRegistry
    }

    def "throws GradleException when setupDescriptor is null"() {
        setup:
        _ * projectLayoutRegistry.get("aType") >> null
        when:
        setupBuild.type = "aType"
        setupBuild.setupProjectLayout()
        then:
        def e = thrown(GradleException)
        e.message == "Declared setup-type 'aType' is not supported."

    }

    def "delegates task action to referenced setupDescriptor"() {
        setup:
        1 * projectLayoutRegistry.supports("empty") >> true
        1 * projectLayoutRegistry.get("empty") >> projectSetupDescriptor
        when:
        setupBuild.setupProjectLayout()
        then:
        1 * projectSetupDescriptor.generateProject()
    }
}
