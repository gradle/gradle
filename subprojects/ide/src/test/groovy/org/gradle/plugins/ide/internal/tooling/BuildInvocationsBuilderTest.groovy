/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.DefaultTask
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification

class BuildInvocationsBuilderTest extends Specification {
    def builder = new BuildInvocationsBuilder()
    @Shared def project = TestUtil.builder().withName("root").build()
    @Shared def child1 = TestUtil.builder().withName("child1").withParent(project).build()
    @Shared def child2 = TestUtil.builder().withName("child2").withParent(child1).build()

    def setupSpec() {
        child2.tasks.create('t1', DefaultTask)
    }

    def "can build model"() {
        expect:
        builder.canBuild(BuildInvocations.name)
    }

    def "builds model"() {
        expect:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.BuildInvocations", startProject)
        model.taskSelectors*.name == selectorNames

        where:
        startProject | selectorNames
        project      | ['t1']
        child1       | ['t1']
        child2       | []
    }
}
