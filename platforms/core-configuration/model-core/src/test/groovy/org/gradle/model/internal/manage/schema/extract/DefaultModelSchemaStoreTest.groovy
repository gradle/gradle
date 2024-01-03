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

import groovy.transform.CompileStatic
import org.gradle.model.Managed
import org.gradle.model.ModelSet
import org.gradle.model.internal.manage.schema.ManagedImplStructSchema
import org.gradle.model.internal.manage.schema.ModelSchema
import org.gradle.model.internal.type.ModelType
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.beans.Introspector
import java.util.concurrent.CopyOnWriteArraySet

class DefaultModelSchemaStoreTest extends ConcurrentSpec {
    def extractor = DefaultModelSchemaExtractor.withDefaultStrategies()
    def store = new DefaultModelSchemaStore(extractor)

    def "caches schema for a type"() {
        // intentionally use two different “instances” of the same type
        def type1 = ModelType.of(SimpleManagedType)
        def type2 = ModelType.of(SimpleManagedType)

        expect:
        store.getSchema(type1).is(store.getSchema(type2))
    }

    def "each thread receives same schema object"() {
        def seen = new CopyOnWriteArraySet()

        when:
        async {
            5.times {
                start {
                    seen << store.getSchema(SimpleManagedType)
                }
            }
        }

        then:
        seen.size() == 1
    }

    def "does not hold strong reference"() {
        given:
        def cl = new GroovyClassLoader(getClass().classLoader)
        addClass(cl, impl)

        expect:
        store.cache.size() > 0

        when:
        cl.clearCache()

        then:
        ConcurrentTestUtil.poll(10) {
            System.gc()
            store.cleanUp()
            store.size() == 0
        }

        where:
        impl << [
                "class SomeThing {}",
                "@${Managed.name} interface SomeThing { SomeThing getThing() }",
                "@${Managed.name} interface SomeThing { ${ModelSet.name}<SomeThing> getThings() }",
                "@${Managed.name} interface SomeThing { @${Managed.name} static interface Child {}; ${ModelSet.name}<Child> getThings() }",
        ]
    }

    def "does not hold strong reference to a managed #type type"() {
        given:
        def cl = new GroovyClassLoader(getClass().classLoader)

        when:
        def schema = addClass(cl, "@${Managed.name} $type ManagedType {}")

        then:
        store.cache.size() > 0

        when:
        forcefullyClearReferences(schema)

        then:
        ConcurrentTestUtil.poll(10) {
            System.gc()
            store.cleanUp()
            store.size() == 0
        }

        where:
        type << ["abstract class", "interface"]
    }

    @CompileStatic
    // must be compile static to avoid call sites being created with soft class refs
    private static void forcefullyClearReferences(ManagedImplStructSchema schema) {
        // Remove strong internal circular ref
        (schema.type.rawClass.classLoader as GroovyClassLoader).clearCache()

        // Remove soft references (dependent on Groovy internals)
        ModelStoreTestUtils.removeClassFromGlobalClassSet(schema.type.rawClass)

        // Remove soft references
        Introspector.flushFromCaches(schema.type.rawClass)
    }

    def "canonicalizes introspection for different sites of generic type"() {
        when:
        def cl = new GroovyClassLoader()
        addClass(cl, "@${Managed.name} interface Thing {}")
        addClass(cl, "@${Managed.name} interface Container1 { ${ModelSet.name}<Thing> getThings() }")
        addClass(cl, "@${Managed.name} interface Container2 { ${ModelSet.name}<Thing> getThings() }")

        then:
        store.cache.size() == 4
    }

    def "caches schema for different instances of same base type"() {
        when:
        def cl = new GroovyClassLoader()
        addClass(cl, "@${Managed.name} interface Thing1 {}")
        addClass(cl, "@${Managed.name} interface Thing2 {}")
        addClass(cl, "@${Managed.name} interface Container1 { ${ModelSet.name}<Thing1> getThings() }")
        addClass(cl, "@${Managed.name} interface Container2 { ${ModelSet.name}<Thing2> getThings() }")

        then:
        store.cache.size() == 6
    }

    private ModelSchema addClass(GroovyClassLoader cl, String impl) {
        def type = cl.parseClass(impl)
        store.getSchema(ModelType.of(type))
    }

}
