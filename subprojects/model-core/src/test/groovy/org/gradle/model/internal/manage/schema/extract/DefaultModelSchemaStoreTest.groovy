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

package org.gradle.model.internal.manage.schema.extract

import org.gradle.model.Managed
import org.gradle.model.collection.ManagedSet
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

class DefaultModelSchemaStoreTest extends Specification {

    def store = new DefaultModelSchemaStore()

    def "can get schema"() {
        // intentionally use two different “instances” of the same type
        def type1 = ModelType.of(SimpleManagedType)
        def type2 = ModelType.of(SimpleManagedType)

        expect:
        store.getSchema(type1).is(store.getSchema(type2))
    }

    @Unroll
    def "does not hold strong reference"() {
        given:
        def cl = new GroovyClassLoader(getClass().classLoader)
        addClass(cl, impl)

        expect:
        store.cache.size() > 0

        when:
        cl.clearCache()

        then:
        new PollingConditions(timeout: 10).eventually {
            System.gc()
            store.cache.cleanUp()
            store.cache.size() == 0
        }

        where:
        impl << [
                "class SomeThing {}",
                "@${Managed.name} interface SomeThing { ${ManagedSet.name}<SomeThing> getThings() }",
                "@${Managed.name} interface SomeThing { @${Managed.name} static interface Child {}; ${ManagedSet.name}<Child> getThings() }",
        ]
    }

    private void addClass(GroovyClassLoader cl, String impl) {
        def type = cl.parseClass(impl)
        store.getSchema(ModelType.of(type))
    }

}
