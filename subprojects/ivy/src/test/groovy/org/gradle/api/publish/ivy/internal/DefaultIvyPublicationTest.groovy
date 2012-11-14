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

package org.gradle.api.publish.ivy.internal

import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.HelperUtil
import spock.lang.Specification

class DefaultIvyPublicationTest extends Specification {

    ProjectInternal project = HelperUtil.createRootProject()

    DefaultIvyPublication publication(String name = "ivy", Configuration... configurations) {
        Instantiator instantiator = project.getServices().get(Instantiator)
        instantiator.newInstance(DefaultIvyPublication, name, instantiator, configurations as Set, project, project.fileResolver, project.tasks)
    }

    def "publishable files are the artifact files"() {
        when:
        def file1 = project.file("file1")
        def descriptorFile1 = project.file("ivy1.xml")
        project.configurations { conf1 }
        project.artifacts { conf1 file1 }
        def p = publication(project.configurations.conf1)
        p.descriptor.file = descriptorFile1

        then:
        p.publishableFiles.files == [file1, descriptorFile1] as Set

        when:
        def file2 = project.file("file2")
        def descriptorFile2 = project.file("ivy2.xml")
        project.configurations { conf2 }
        project.artifacts { conf2 file2 }
        p = publication(project.configurations.conf1, project.configurations.conf2)
        p.descriptor.file = descriptorFile2

        then:
        p.publishableFiles.files == [file1, file2, descriptorFile2] as Set
    }

    def "publication is built by what builds the artifacts and descriptor"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        Task dummyTask = project.task("dummyTask")

        when:
        def task1 = project.tasks.add("task1", Jar)
        task1.baseName = "task1"
        project.configurations { conf1 }
        project.artifacts { conf1 task1 }
        def p = publication(project.configurations.conf1)

        then:
        p.buildDependencies.getDependencies(dummyTask) == [task1] as Set

        when:
        def task2 = project.tasks.add("task2", Jar)
        project.configurations { conf2 }
        project.artifacts { conf2 task2 }
        p = publication(project.configurations.conf1, project.configurations.conf2)

        then:
        p.buildDependencies.getDependencies(dummyTask) == [task1, task2] as Set

        when:
        def task3 = project.tasks.add("task3")
        p.descriptor.builtBy(task3)

        then:
        p.buildDependencies.getDependencies(dummyTask) == [task1, task2, task3] as Set
    }

    def "can get publishable files when no descriptor file set"() {
        when:
        def file1 = project.file("file1")
        project.configurations { conf1 }
        project.artifacts { conf1 file1 }
        def p = publication(project.configurations.conf1)

        then:
        p.descriptor.file == null
        p.publishableFiles.files == [file1] as Set
    }
}
