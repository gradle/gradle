/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.base.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetFactory;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.model.internal.core.Hidden;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.schema.extract.FactoryBasedStructNodeInitializerExtractionStrategy;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.TypeBuilder;

/**
 * Base plugin for language support.
 *
 * - Adds a {@link ProjectSourceSet} named {@code sources} to the project.
 * - Registers the base {@link LanguageSourceSet} type.
 */
@Incubating
public class LanguageBasePlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Hidden
        @Model
        LanguageSourceSetFactory languageSourceSetFactory(ServiceRegistry serviceRegistry) {
            return new LanguageSourceSetFactory("sourceSets", serviceRegistry.get(SourceDirectorySetFactory.class));
        }

        @LanguageType
        void registerBaseLanguageSourceSet(TypeBuilder<LanguageSourceSet> builder) {
            builder.defaultImplementation(BaseLanguageSourceSet.class);
            builder.internalView(LanguageSourceSetInternal.class);
        }

        @Mutate
        void registerSourceSetNodeInitializer(NodeInitializerRegistry nodeInitializerRegistry, LanguageSourceSetFactory languageSourceSetFactory, StructBindingsStore bindingsStore) {
            nodeInitializerRegistry.registerStrategy(new FactoryBasedStructNodeInitializerExtractionStrategy<LanguageSourceSet>(languageSourceSetFactory, bindingsStore));
        }

        @Model
        ProjectSourceSet sources(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultProjectSourceSet.class);
        }

        @Validate
        void validateLanguageSourceSetRegistrations(LanguageSourceSetFactory instanceFactory) {
            instanceFactory.validateRegistrations();
        }
    }
}
