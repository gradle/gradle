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
import org.gradle.tooling.internal.gradle.TaskListingLaunchable
import org.gradle.tooling.internal.protocol.InternalLaunchable
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.TaskSelector
import spock.lang.Specification

class ConsumerOperationParametersTest extends Specification {

    def "null or empty arguments have the same meaning"() {
        def params = ConsumerOperationParameters.builder()
        when:
        params.arguments = null

        then:
        params.build().arguments == null

        when:
        params.arguments = []

        then:
        params.build().arguments == null

        when:
        params.arguments = ['-Dfoo']

        then:
        params.build().arguments == ['-Dfoo']
    }

    def "null or empty jvm arguments have the same meaning"() {
        def params = ConsumerOperationParameters.builder()
        when:
        params.jvmArguments = null

        then:
        params.build().jvmArguments == null

        when:
        params.jvmArguments = []

        then:
        params.build().jvmArguments == null

        when:
        params.jvmArguments = ['-Xmx']

        then:
        params.build().jvmArguments == ['-Xmx']
    }

    def "task names and empty launchables"() {
        def builder = ConsumerOperationParameters.builder()
        when:
        builder.tasks = ['a', 'b']
        def params = builder.build()

        then:
        params.tasks == ['a', 'b']
        params.launchables == null
    }

    interface LaunchableImpl extends Launchable, TaskSelector, InternalLaunchable {}

    def "launchables from provider"() {
        def builder = ConsumerOperationParameters.builder()
        when:
        def launchable1 = Mock(LaunchableImpl)
        def launchable2 = Mock(LaunchableImpl)
        builder.launchables = [launchable1, launchable2]
        def params = builder.build()

        then:
        params.tasks == []
        params.launchables == [launchable1, launchable2]
    }

    interface AdaptedLaunchable extends Launchable, TaskListingLaunchable {}

    def "launchables from adapters"() {
        def builder = ConsumerOperationParameters.builder()
        when:
        def launchable1 = Mock(AdaptedLaunchable)
        def paths1 = Sets.newTreeSet()
        paths1.add(':a')
        _ * launchable1.taskNames >> paths1
        def launchable2 = Mock(AdaptedLaunchable)
        def paths2 = Sets.newTreeSet()
        paths2.add(':b')
        paths2.add(':lib:b')
        _ * launchable2.taskNames >> paths2
        builder.launchables = [launchable1, launchable2]
        def params = builder.build()

        then:
        params.tasks == [':a', ':b', ':lib:b']
        params.launchables == []
    }
}
