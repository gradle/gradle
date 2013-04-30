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

import org.gradle.buildsetup.plugins.ProjectSetupDescriptor
import org.gradle.util.HelperUtil
import spock.lang.Specification


class ProjectLayoutSetupSpec extends Specification {

    ProjectLayoutSetup projectLayoutSetupTask;

    def setup() {
        projectLayoutSetupTask = HelperUtil.builder().build().tasks.create("projectLayoutSetup", ProjectLayoutSetup)
    }

    def "onlyIf references setupDescriptor"() {
        setup:
        org.gradle.internal.Factory onlyIfFactory = Mock(org.gradle.internal.Factory)
        ProjectSetupDescriptor projectSetupDescriptor = Mock(ProjectSetupDescriptor)
        projectLayoutSetupTask.projectSetupDescriptor = projectSetupDescriptor
        when:
        projectLayoutSetupTask.getOnlyIf().isSatisfiedBy(projectLayoutSetupTask)
        then:
        1 * onlyIfFactory.create() >> false
        1 * projectSetupDescriptor.onlyIf >> onlyIfFactory
    }

    def "delegates task action to referenced setupDescriptor"() {
        setup:
        ProjectSetupDescriptor projectSetupDescriptor = Mock(ProjectSetupDescriptor)
        projectLayoutSetupTask.projectSetupDescriptor = projectSetupDescriptor
        when:
        projectLayoutSetupTask.setupProjectLayout()
        then:
        1 * projectSetupDescriptor.setupLayout(_)
    }
}
