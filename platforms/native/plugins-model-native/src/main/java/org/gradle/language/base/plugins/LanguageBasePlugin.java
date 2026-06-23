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
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.Model;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.plugins.ComponentBasePlugin;

/**
 * Base plugin for language support.
 *
 * - Adds a {@link ProjectSourceSet} named {@code sources} to the project.
 * - Registers the base {@link LanguageSourceSet} type.
 */
@Incubating
public abstract class LanguageBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ComponentBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        void registerBaseLanguageSourceSet(TypeBuilder<LanguageSourceSet> builder) {
            builder.defaultImplementation(BaseLanguageSourceSet.class);
            builder.internalView(LanguageSourceSetInternal.class);
        }

        @Model
        ProjectSourceSet sources(Instantiator instantiator, CollectionCallbackActionDecorator decorator) {
            return instantiator.newInstance(DefaultProjectSourceSet.class, decorator);
        }
    }
}
