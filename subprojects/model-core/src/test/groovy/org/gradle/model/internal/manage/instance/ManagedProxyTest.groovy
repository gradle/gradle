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

package org.gradle.model.internal.manage.instance

import org.gradle.api.Action
import org.gradle.model.Managed
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.fixture.TestManagedProxyFactory
import org.gradle.model.internal.manage.schema.StructSchema
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import spock.lang.Specification

class ManagedProxyTest extends Specification {

    def schemaStore = DefaultModelSchemaStore.instance
    def factory = TestManagedProxyFactory.INSTANCE

    @Managed
    static private interface ManagedType {
        ManagedType getSelf()
    }

    def "a useful type name is used in stacktrace for a generated managed model type"() {
        def state = [get: { throw new RuntimeException("from state") }] as ModelElementState

        given:
        def proxy = factory.createProxy(state, schemaStore.getSchema(ManagedType), null)

        when:
        proxy.self

        then:
        RuntimeException e = thrown()
        e.message == "from state"
        e.stackTrace.any { it.className == ManagedType.name + "\$Impl" && it.methodName == "getSelf" }
    }

    static class UnmanagedTypeWithActionImpl implements UnmanagedTypeWithAction {
        private String thing

        @Override
        void thing(Action<? super String> action) {
            action.execute(thing)
        }

        String getThing() {
            return thing
        }

        void setThing(String thing) {
            this.thing = thing
        }
    }

    static interface UnmanagedTypeWithAction {
        String getThing()
        void setThing(String thing)
        void thing(Action<? super String> action);
    }

    @Managed
    static interface ManagedTypeWithAction extends UnmanagedTypeWithAction {
    }

    def "decorates generated type"() {
        def state = Mock(ModelElementState)
        def backingNode = Mock(MutableModelNode)
        def delegate = new UnmanagedTypeWithActionImpl()

        when:
        def proxy = factory.createProxy(
            state,
            (StructSchema<ManagedTypeWithAction>) schemaStore.getSchema(ManagedTypeWithAction),
            schemaStore.getSchema(UnmanagedTypeWithActionImpl)
        )
        proxy.thing = "12"

        then:
        proxy.thing { thing ->
            assert thing == "12"
        }
        _ * state.backingNode >> backingNode
        _ * backingNode.getPrivateData(_) >> delegate
    }
}
