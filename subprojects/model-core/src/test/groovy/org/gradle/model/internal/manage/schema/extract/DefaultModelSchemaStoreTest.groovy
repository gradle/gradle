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
import org.codehaus.groovy.reflection.ClassInfo
import org.gradle.model.Managed
import org.gradle.model.ModelSet
import org.gradle.model.internal.manage.schema.ModelStructSchema
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.beans.Introspector

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

    @Unroll
    def "does not hold strong reference to a managed #type type"() {
        given:
        def modelType = ModelType.of(new GroovyClassLoader(getClass().classLoader).parseClass("@${Managed.name} $type ManagedType {}"))

        when:
        def schema = store.getSchema(modelType)

        then:
        store.cache.size() > 0

        when:
        forcefullyClearReferences(schema)

        then:
        new PollingConditions(timeout: 10).eventually {
            System.gc()
            store.cleanUp()
            store.size() == 0
            schema.managedImpl == null // collected too
        }

        where:
        type << ["abstract class", "interface"]
    }

    @CompileStatic
    // must be compile static to avoid call sites being created with soft class refs
    private static void forcefullyClearReferences(ModelStructSchema schema) {
        // Remove strong internal circular ref
        (schema.type.rawClass.classLoader as GroovyClassLoader).clearCache()

        // Remove soft references (dependent on Groovy internals)
        def f = ClassInfo.getDeclaredField("globalClassSet")
        f.setAccessible(true)
        ClassInfo.ClassInfoSet globalClassSet = f.get(null) as ClassInfo.ClassInfoSet
        globalClassSet.remove(schema.type.rawClass)
        globalClassSet.remove(schema.managedImpl)

        // Remove soft references
        Introspector.flushFromCaches(schema.type.rawClass)
        Introspector.flushFromCaches(schema.managedImpl)
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

    private void addClass(GroovyClassLoader cl, String impl) {
        def type = cl.parseClass(impl)
        store.getSchema(ModelType.of(type))
    }

}
