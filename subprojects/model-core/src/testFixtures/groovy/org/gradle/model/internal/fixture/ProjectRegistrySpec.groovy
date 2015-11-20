/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.fixture
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.typeconversion.TypeConverter
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.manage.instance.ManagedProxyFactory
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import spock.lang.Specification

class ProjectRegistrySpec extends Specification {
    def registry = new ModelRegistryHelper()
    def schemaStore = DefaultModelSchemaStore.instance
    def proxyFactory = TestManagedProxyFactory.INSTANCE
    def nodeInitializerRegistry = TestNodeInitializerRegistry.INSTANCE

    def setup() {
        registry.register(ModelRegistrations.serviceInstance(ModelReference.of("schemaStore", ModelSchemaStore), DefaultModelSchemaStore.instance).build())
        registry.register(ModelRegistrations.serviceInstance(ModelReference.of("proxyFactory", ManagedProxyFactory), proxyFactory).build())
        registry.register(ModelRegistrations.serviceInstance(ModelReference.of("serviceRegistry", ServiceRegistry), Mock(ServiceRegistry)).build())
        registry.register(ModelRegistrations.serviceInstance(ModelReference.of("typeConverter", TypeConverter), Mock(TypeConverter)).build())
        registry.register(ModelRegistrations.serviceInstance(DefaultNodeInitializerRegistry.DEFAULT_REFERENCE, nodeInitializerRegistry).build())
    }
}
