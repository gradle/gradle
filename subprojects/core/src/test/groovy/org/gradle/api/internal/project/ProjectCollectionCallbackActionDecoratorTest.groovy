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

package org.gradle.api.internal.project

import org.gradle.api.Action
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.specs.Spec
import spock.lang.Specification

import java.util.function.Supplier

class ProjectCollectionCallbackActionDecoratorTest extends Specification {
    def delegate = Mock(CollectionCallbackActionDecorator)
    def owner = Mock(ProjectState)
    def decorator = new ProjectCollectionCallbackActionDecorator(delegate, owner)

    def "runs realization with the lock of the owning project"() {
        def realization = Mock(Runnable)

        when:
        decorator.runRealization(realization)

        then:
        1 * owner.runWithModelLockIfAccessedFromOtherProject(_) >> { Supplier<?> action -> action.get() }

        then:
        1 * realization.run()
        0 * _
    }

    def "delegates decoration of callbacks"() {
        def action = Mock(Action)
        def decoratedAction = Mock(Action)
        def spec = Mock(Spec)
        def decoratedSpec = Mock(Spec)

        when:
        def resultAction = decorator.decorate(action)
        def resultSpec = decorator.decorateSpec(spec)

        then:
        1 * delegate.decorate(action) >> decoratedAction
        1 * delegate.decorateSpec(spec) >> decoratedSpec
        0 * _

        and:
        resultAction == decoratedAction
        resultSpec == decoratedSpec
    }
}
