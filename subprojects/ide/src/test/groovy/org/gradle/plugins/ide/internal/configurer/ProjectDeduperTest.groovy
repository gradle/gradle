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

class ProjectDeduperTest extends Specification {

    def project = HelperUtil.createRootProject()
    def childProject = HelperUtil.createChildProject(project, "child", new File("."))
    def grandChildProject = HelperUtil.createChildProject(childProject, "grandChild", new File("."))

    def deduper = new ProjectDeduper()

    def "should order projects by depth and pass them to module deduper"() {
        given:
        deduper.moduleNameDeduper = Mock(ModuleNameDeduper)
        def unordered = [childProject, grandChildProject, project]

        when:
        deduper.dedupe(unordered, { return it })

        then:
        1 * deduper.moduleNameDeduper.dedupe([project, childProject, grandChildProject])
    }
}
