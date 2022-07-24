/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.Ivy
import org.apache.ivy.core.IvyContext
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultIvyContextManagerTest extends ConcurrentSpec {
    final manager = new DefaultIvyContextManager()

    def setup() {
        IvyContext.currentStack.clear()
    }

    def "executes action against an Ivy instance"() {
        def action = Mock(Action)

        when:
        manager.withIvy(action)

        then:
        1 * action.execute({it != null})
        0 * action._
    }

    def "executes action against an Ivy instance and returns the result"() {
        def action = Mock(Transformer)

        when:
        def result = manager.withIvy(action)

        then:
        result == "result"

        and:
        1 * action.transform({it != null}) >> "result"
        0 * action._
    }

    def "nested actions are executed against the same Ivy instance"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def transformer = Mock(Transformer)
        def ivy

        when:
        manager.withIvy(action1)

        then:
        1 * action1.execute(_) >> { Ivy param ->
            ivy = param
            manager.withIvy(transformer)
        }
        1 * transformer.transform(_) >> { Ivy param ->
            assert param.is(ivy)
            manager.withIvy(action2)
        }
        1 * action2.execute(_) >> { Ivy param ->
            assert param.is(ivy)
        }
        0 * _._
    }

    def "sets up Ivy context stack and cleans up after action"() {
        given:
        def action = Mock(Action)

        when:
        manager.withIvy(action)

        then:
        1 * action.execute(_) >> { Ivy ivy ->
            assert IvyContext.context.ivy.is(ivy)
        }

        and:
        IvyContext.currentStack.empty()
    }

    def "cleans up after failed action"() {
        given:
        def action = Mock(Action)
        def failure = new RuntimeException()

        and:
        action.execute(_) >> { Ivy ivy ->
            throw failure
        }

        when:
        manager.withIvy(action)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        IvyContext.currentStack.empty()
    }

    def "reuses Ivy and IvySettings instances"() {
        given:
        def action = Mock(Action)
        def ivy
        def ivySettings

        when:
        manager.withIvy(action)

        then:
        1 * action.execute(_) >> { Ivy param ->
            ivy = param
            ivySettings = param.settings
        }

        when:
        manager.withIvy(action)

        then:
        1 * action.execute(_) >> { Ivy param ->
            assert param.is(ivy)
            assert param.settings.is(ivySettings)
            assert IvyContext.context.ivy.is(ivy)
        }

        when:
        async {
            start {
                manager.withIvy(action)
            }
        }

        then:
        1 * action.execute(_) >> { Ivy param ->
            assert param.is(ivy)
            assert param.settings.is(ivySettings)
            assert IvyContext.context.ivy.is(ivy)
        }

        and:
        IvyContext.currentStack.empty()
    }

    def "resets Ivy settings on reuse"() {
        given:
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def ivy
        def ivySettings

        when:
        manager.withIvy(action1)

        then:
        1 * action1.execute(_) >> { Ivy param ->
            ivy = param
            ivySettings = param.settings
            ivySettings.addResolver(Stub(DependencyResolver) { getName() >> "some-resolver" })
            ivySettings.setDefaultResolver("some-resolver")
        }

        when:
        manager.withIvy(action2)

        then:
        1 * action2.execute(_) >> { Ivy param ->
            assert param.settings.is(ivySettings)
            assert ivySettings.defaultResolverName == null
            assert ivySettings.resolvers.empty
            assert ivySettings.resolverNames.empty
        }
    }

    def "each thread is given a separate Ivy instance and context"() {
        given:
        def ivy1
        def ivySettings1
        def ivy2
        def ivySettings2

        when:
        async {
            start {
                manager.withIvy({ param ->
                    instant.action1
                    thread.blockUntil.action2
                    ivy1 = param
                    ivySettings1 = param.settings
                    assert IvyContext.context.ivy.is(ivy1)
                } as Action)
            }
            start {
                manager.withIvy({ param ->
                    instant.action2
                    thread.blockUntil.action1
                    ivy2 = param
                    ivySettings2 = param.settings
                    assert IvyContext.context.ivy.is(ivy2)
                } as Action)
            }
        }

        then:
        !ivy1.is(ivy2)
        !ivySettings1.is(ivySettings2)
    }
}
