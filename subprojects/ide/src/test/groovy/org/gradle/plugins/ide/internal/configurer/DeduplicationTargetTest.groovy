/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.internal.configurer

import org.gradle.util.HelperUtil
import spock.lang.Specification

class DeduplicationTargetTest extends Specification {

    def "knows candidate names"() {
        when:
        def project = HelperUtil.createRootProject()
        assert project.name == 'test'
        def childProject = HelperUtil.createChildProject(project, "child", new File("."))
        def grandChildProject = HelperUtil.createChildProject(childProject, "grandChild", new File("."))

        then:
        new DeduplicationTarget(project: project, moduleName: 'test' ).candidateNames == ['test']
        new DeduplicationTarget(project: childProject, moduleName: 'child' ).candidateNames == ['child', 'test-child']
        new DeduplicationTarget(project: grandChildProject, moduleName: 'grandChild' ).candidateNames == ['grandChild', 'child-grandChild', 'test-child-grandChild']
    }

    def "uses passed module name instead of project name"() {
        when:
        def project = HelperUtil.createRootProject()
        assert project.name == 'test'
        def childProject = HelperUtil.createChildProject(project, "child", new File("."))

        then:
        new DeduplicationTarget(project: project, moduleName: 'ROOT' ).candidateNames == ['ROOT']
        new DeduplicationTarget(project: childProject, moduleName: 'CHILD' ).candidateNames == ['CHILD', 'test-CHILD']
    }
}
