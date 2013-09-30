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
package org.gradle.language.assembler.plugins
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.assembler.AssemblerSourceSet
import org.gradle.language.assembler.internal.DefaultAssemblerSourceSet
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.plugins.LanguageBasePlugin

import javax.inject.Inject
/**
 * Adds core Assembler language support.
 *
 * <ul>
 *     <li>For any {@link FunctionalSourceSet}, adds a conventional {@link AssemblerSourceSet} called 'asm'.</li>
 *     <li>Establishes a convention for all {@link AssemblerSourceSet}s so that sources are located in 'src/<name>/asm'.</li>
 *     <li>
 * </ul>
 */
@Incubating
class AssemblerLangPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;

    @Inject
    public AssemblerLangPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    void apply(ProjectInternal project) {
        project.getPlugins().apply(LanguageBasePlugin.class);

        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        projectSourceSet.all(new Action<FunctionalSourceSet>() {
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                functionalSourceSet.registerFactory(AssemblerSourceSet) { name ->
                    instantiator.newInstance(DefaultAssemblerSourceSet, name, functionalSourceSet, project)
                }

                // Create a single assembler source set
                functionalSourceSet.create "asm", AssemblerSourceSet
            }
        });
    }
}