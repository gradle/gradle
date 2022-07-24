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
import org.gradle.model.internal.core.NodeInitializerRegistry
import org.gradle.model.internal.inspect.ModelRuleExtractor
import org.gradle.model.internal.manage.binding.StructBindingsStore
import org.gradle.model.internal.manage.instance.ManagedProxyFactory
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.ClassRule

@SuppressWarnings("GrMethodMayBeStatic")
class ProjectRegistrySpec extends AbstractProjectBuilderSpec {
    public static final ModelSchemaStore SCHEMA_STORE
    public static final ManagedProxyFactory MANAGED_PROXY_FACTORY
    public static final ModelRuleExtractor MODEL_RULE_EXTRACTOR
    public static final NodeInitializerRegistry NODE_INITIALIZER_REGISTRY
    public static final StructBindingsStore STRUCT_BINDINGS_STORE

    @ClassRule
    public static final TestNameTestDirectoryProvider SERVICES_TEST_DIRECTORY = TestNameTestDirectoryProvider.newInstance(ProjectRegistrySpec.class)

    static {
        def services = TestUtil.create(SERVICES_TEST_DIRECTORY.testDirectory).rootProject().services
        SCHEMA_STORE = services.get(ModelSchemaStore)
        MANAGED_PROXY_FACTORY = services.get(ManagedProxyFactory)
        MODEL_RULE_EXTRACTOR = services.get(ModelRuleExtractor)
        STRUCT_BINDINGS_STORE = services.get(StructBindingsStore)
        NODE_INITIALIZER_REGISTRY = new DefaultNodeInitializerRegistry(SCHEMA_STORE, STRUCT_BINDINGS_STORE)

        // Class rule does not always clean this up, so clean it up now
        SERVICES_TEST_DIRECTORY.testDirectory.deleteDir()
    }

    ModelRegistry registry = createModelRegistry()
    ModelSchemaStore schemaStore = SCHEMA_STORE
    ManagedProxyFactory proxyFactory = MANAGED_PROXY_FACTORY
    ModelRuleExtractor modelRuleExtractor = MODEL_RULE_EXTRACTOR
    StructBindingsStore structBindingsStore = STRUCT_BINDINGS_STORE
    NodeInitializerRegistry nodeInitializerRegistry = createNodeInitializerRegistry(schemaStore, structBindingsStore)

    def setup() {
        registerService "schemaStore", ModelSchemaStore, schemaStore
        registerService "proxyFactory", ManagedProxyFactory, proxyFactory
        registerService "serviceRegistry", ServiceRegistry, Mock(ServiceRegistry)
        registerService "typeConverter", TypeConverter, Mock(TypeConverter)
        registerService "structBindingsStore", StructBindingsStore, structBindingsStore
        registerService "nodeInitializerRegistry", NodeInitializerRegistry, nodeInitializerRegistry
    }

    protected ModelRegistry createModelRegistry() {
        return new ModelRegistryHelper()
    }

    protected NodeInitializerRegistry createNodeInitializerRegistry(ModelSchemaStore schemaStore, StructBindingsStore structBindingsStore) {
        return NODE_INITIALIZER_REGISTRY
    }

    protected <T> void registerService(String path, Class<T> type, T instance) {
        registry.register(ModelRegistrations.serviceInstance(ModelReference.of(path, type), instance).descriptor("register service '$path'").build())
    }
}
