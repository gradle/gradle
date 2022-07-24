/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.tooling.internal.consumer.parameters

import com.google.common.collect.Sets
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.internal.consumer.converters.FixedBuildIdentifierProvider
import org.gradle.tooling.internal.gradle.TaskListingLaunchable
import org.gradle.tooling.internal.protocol.InternalLaunchable
import org.gradle.tooling.model.TaskSelector
import spock.lang.Specification

class ConsumerOperationParametersTest extends Specification {

    def builder = ConsumerOperationParameters.builder().setEntryPoint("entry-point")

    def "can build consumer operation parameters for provided properties"() {
        given:
        def tasks = ['a', 'b']
        def classpath = DefaultClassPath.of(new File('/Users/foo/bar/test.jar'), new File('/Users/foo/bar/resources'))

        when:
        builder.tasks = tasks
        builder.injectedPluginClasspath = classpath
        def params = builder.build()

        then:
        params.tasks == tasks
        params.injectedPluginClasspath == classpath.asFiles
        params.launchables == null
    }

    def "launchables from provider"() {
        when:
        def launchable1 = Mock(InternalLaunchable)
        def launchable2 = Mock(InternalLaunchable)
        builder.launchables = [adapt(launchable1), adapt(launchable2)]
        def params = builder.build()

        then:
        params.tasks == []
        params.launchables == [launchable1, launchable2]
    }

    def "launchables from adapters"() {
        when:
        def launchable1 = Mock(TaskListingLaunchable)
        def paths1 = Sets.newTreeSet()
        paths1.add(':a')
        _ * launchable1.taskNames >> paths1
        def launchable2 = Mock(TaskListingLaunchable)
        def paths2 = Sets.newTreeSet()
        paths2.add(':b')
        paths2.add(':lib:b')
        _ * launchable2.taskNames >> paths2
        builder.launchables = [adapt(launchable1), adapt(launchable2)]
        def params = builder.build()

        then:
        params.tasks == [':a', ':b', ':lib:b']
        params.launchables == null
    }

    def adapt(def object) {
        return new FixedBuildIdentifierProvider(id()).applyTo(new ProtocolToModelAdapter().builder(TaskSelector)).build(object)
    }

    def id() {
        return new DefaultProjectIdentifier(new DefaultBuildIdentifier(new File("foo")), ":")
    }
}
