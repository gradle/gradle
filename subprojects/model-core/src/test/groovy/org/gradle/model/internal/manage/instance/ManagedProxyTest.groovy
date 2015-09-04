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

import org.gradle.model.Managed
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class ManagedProxyTest extends Specification {

    def factory = ManagedProxyFactory.INSTANCE

    @Managed
    static private interface ManagedType {
        ManagedType getSelf()
    }

    def "a useful type name is used in stacktrace for a generated managed model type"() {
        given:
        def proxy = factory.createProxy([get: { throw new RuntimeException("from state") }] as ModelElementState, DefaultModelSchemaStore.instance.getSchema(ModelType.of(ManagedType)))

        when:
        proxy.self

        then:
        RuntimeException e = thrown()
        e.message == "from state"
        e.stackTrace.any { it.className == ManagedType.name + "_Impl" && it.methodName == "getSelf" }
    }
}
