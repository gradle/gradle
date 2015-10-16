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

package org.gradle.platform.base.internal.registry;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.resolve.DefaultProjectModelResolver;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.language.base.internal.resolve.DependentSourceSetLocalComponentConverter;
import org.gradle.model.internal.inspect.MethodModelRuleExtractor;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractionStrategy;
import org.gradle.platform.base.internal.VariantAspectExtractionStrategy;

public class ComponentModelBaseServiceRegistry implements PluginServiceRegistry {

    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    public void registerBuildSessionServices(ServiceRegistration registration) {

    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    public void registerGradleServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildScopeServices {
        DependentSourceSetLocalComponentConverter createLocalComponentFactory() {
            return new DependentSourceSetLocalComponentConverter();
        }

        ProjectModelResolver createProjectLocator(final ProjectRegistry<ProjectInternal> projectRegistry) {
            return new DefaultProjectModelResolver(projectRegistry);
        }
    }

    private static class GlobalScopeServices {
        MethodModelRuleExtractor createLanguageTypePluginInspector(ModelSchemaStore schemaStore) {
            return new LanguageTypeModelRuleExtractor(schemaStore);
        }

        MethodModelRuleExtractor createComponentModelPluginInspector(ModelSchemaStore schemaStore) {
            return new ComponentTypeModelRuleExtractor(schemaStore);
        }

        MethodModelRuleExtractor createBinaryTypeModelPluginInspector(ModelSchemaStore schemaStore) {
            return new BinaryTypeModelRuleExtractor(schemaStore);
        }

        MethodModelRuleExtractor createComponentBinariesPluginInspector() {
            return new ComponentBinariesModelRuleExtractor();
        }

        MethodModelRuleExtractor createBinaryTaskPluginInspector() {
            return new BinaryTasksModelRuleExtractor();
        }

        ModelSchemaAspectExtractionStrategy createVariantAspectExtractionStrategy() {
            return new VariantAspectExtractionStrategy();
        }
    }
}
