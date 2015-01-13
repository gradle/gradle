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

package org.gradle.model.internal.core

import org.gradle.api.internal.ClosureBackedAction
import org.gradle.internal.BiAction
import org.gradle.model.Managed
import org.gradle.model.collection.ManagedSet
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.inspect.DefaultModelCreatorFactory
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class ManagedSetModelProjectionTest extends Specification {
    @Managed
    interface NamedThing {
        String getName()

        void setName(String name);
    }

    def collectionPath = ModelPath.path("collection")
    def collectionType = new ModelType<ManagedSet<NamedThing>>() {}
    def schemaStore = DefaultModelSchemaStore.instance
    def factory = new DefaultModelCreatorFactory(schemaStore)
    def registry = new DefaultModelRegistry()

    def setup() {
        registry.create(
                factory.creator(
                        new SimpleModelRuleDescriptor("define collection"),
                        collectionPath,
                        schemaStore.getSchema(collectionType),
                        [],
                        { value, inputs -> } as BiAction))
    }

    void mutate(@DelegatesTo(ManagedSet) Closure<? super ManagedSet<NamedThing>> action) {
        def mutator = Stub(ModelAction)
        mutator.subject >> ModelReference.of(collectionPath, new ModelType<ManagedSet<NamedThing>>() {})
        mutator.descriptor >> new SimpleModelRuleDescriptor("mutate collection")
        mutator.execute(_, _, _) >> { new ClosureBackedAction<NamedThing>(action).execute(it[1]) }

        registry.apply(ModelActionRole.Mutate, mutator)
        registry.realizeNode(collectionPath)
    }

    def "can define and query elements"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        def set = registry.realize(collectionPath, collectionType)
        set*.name == ['1', '2']
        set.toArray().collect { it.name } == ['1', '2']
        set.toArray(new NamedThing[2]).collect { it.name } == ['1', '2']
    }

    def "reuses element views"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        def set = registry.realize(collectionPath, collectionType)
        def e1 = set.find { it.name == '1' }
        def e2 = set.find { it.name == '1' }
        e1.is(e2)
    }

    def "can query set size"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        !registry.realize(collectionPath, collectionType).isEmpty()
        registry.realize(collectionPath, collectionType).size() == 2
    }

    def "can query set membership"() {
        when:
        mutate {
            create { name = '1' }
            create { name = '2' }
        }

        then:
        def set = registry.realize(collectionPath, collectionType)
        set.contains(set.find { it.name == '1' })
        !set.contains("green")
        !set.contains({} as NamedThing)

        set.containsAll(set)
        set.containsAll(set as List)
        set.containsAll(set.findAll { it.name == '1' })
        !set.containsAll(["green"])
        !set.containsAll([{} as NamedThing])
    }

}
