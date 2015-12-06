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
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.NodeInitializerRegistry
import org.gradle.model.internal.manage.instance.ManagedProxyFactory
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import spock.lang.Specification

@SuppressWarnings("GrMethodMayBeStatic")
class ProjectRegistrySpec extends Specification {
    ModelRegistry registry
    ModelSchemaStore schemaStore
    ManagedProxyFactory proxyFactory
    NodeInitializerRegistry nodeInitializerRegistry

    def setup() {
        registry = createModelRegistry()
        schemaStore = createModelSchemaStore()
        proxyFactory = createManagedProxyFactory()
        nodeInitializerRegistry = createNodeInitializerRegistry()
        registerService "schemaStore", ModelSchemaStore, DefaultModelSchemaStore.instance
        registerService "proxyFactory", ManagedProxyFactory, proxyFactory
        registerService "serviceRegistry", ServiceRegistry, Mock(ServiceRegistry)
        registerService "typeConverter", TypeConverter, Mock(TypeConverter)
        registerService "nodeInitializerRegistry", NodeInitializerRegistry, nodeInitializerRegistry
    }

    protected ModelRegistry createModelRegistry() {
        return new ModelRegistryHelper()
    }

    protected ModelSchemaStore createModelSchemaStore() {
        return DefaultModelSchemaStore.instance
    }

    protected ManagedProxyFactory createManagedProxyFactory() {
        return ManagedProxyFactory.INSTANCE
    }

    protected NodeInitializerRegistry createNodeInitializerRegistry() {
        return TestNodeInitializerRegistry.INSTANCE
    }

    protected <T> void registerService(String path, Class<T> type, T instance) {
        registry.register(ModelRegistrations.serviceInstance(ModelReference.of(path, type), instance).descriptor("register service '$path'").build())
    }
}
