/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import spock.lang.Specification

class DefaultTaskShadowingRegistryTest extends Specification {
    def registry = new DefaultTaskShadowingRegistry()

    interface PublicTask extends Task {}
    interface ShadowTask extends Task {}

    def "returns original type when no shadowing registered"() {
        expect:
        registry.getShadowType(PublicTask) == PublicTask
    }

    def "returns shadow type when registered"() {
        given:
        registry.registerShadowing(PublicTask, ShadowTask, { obj, type -> obj })

        expect:
        registry.getShadowType(PublicTask) == ShadowTask
    }

    def "wraps task instance when registered"() {
        given:
        def shadowTask = Mock(ShadowTask)
        def publicTask = Mock(PublicTask)
        registry.registerShadowing(PublicTask, ShadowTask, { obj, type ->
            obj == shadowTask ? publicTask : obj
        })

        expect:
        registry.maybeWrap(shadowTask, PublicTask) == publicTask
        registry.maybeWrap(Mock(Task), Task) != publicTask
    }

    def "wraps task provider when registered"() {
        given:
        def shadowProvider = Mock(TaskProvider)
        def publicProvider = Mock(TaskProvider)
        registry.registerShadowing(PublicTask, ShadowTask, { obj, type ->
            obj == shadowProvider ? publicProvider : obj
        })

        expect:
        registry.maybeWrapProvider(shadowProvider, PublicTask) == publicProvider
    }

    def "wraps task collection when registered"() {
        given:
        def shadowCollection = Mock(TaskCollection)
        def publicCollection = Mock(TaskCollection)
        registry.registerShadowing(PublicTask, ShadowTask, { obj, type ->
            obj == shadowCollection ? publicCollection : obj
        })

        expect:
        registry.maybeWrapCollection(shadowCollection, PublicTask) == publicCollection
    }

    def "wraps action when registered"() {
        given:
        def shadowAction = Mock(Action)
        def publicAction = Mock(Action)
        registry.registerShadowing(PublicTask, ShadowTask, { obj, type ->
            obj == shadowAction ? publicAction : obj
        })

        expect:
        registry.maybeWrapAction(shadowAction, PublicTask) == publicAction
    }
}
