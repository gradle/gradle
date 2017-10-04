/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.language.c.tasks.CCompile;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.language.nativeplatform.tasks.DiscoverInputs;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChain;

import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Creates discoverInputs tasks for each supported native compile task.
 *
 * Currently, {@link CCompile} and {@link CppCompile} are supported.
 *
 * @since 4.3
 */
@Incubating
@NonNullApi
public class DiscoveredInputsPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        CreateDiscoveredInputsTaskAction createDiscoveredInputsAction = new CreateDiscoveredInputsTaskAction(project);
        project.getTasks().withType(CppCompile.class, createDiscoveredInputsAction);
        project.getTasks().withType(CCompile.class, createDiscoveredInputsAction);
    }

    private static class CreateDiscoveredInputsTaskAction implements Action<AbstractNativeCompileTask> {
        private final Project project;

        public CreateDiscoveredInputsTaskAction(Project project) {
            this.project = project;
        }

        @Override
        public void execute(final AbstractNativeCompileTask compile) {
            DiscoverInputs discoverInputs = project.getTasks().create("discoverInputsFor" + capitalize(compile.getName()), DiscoverInputs.class);
            discoverInputs.source(compile.getSource());
            discoverInputs.includes(compile.getIncludes());
            discoverInputs.getDiscoveredInputs().set(project.getLayout().getBuildDirectory().file(discoverInputs.getName() + "/" + "inputs.txt"));
            discoverInputs.getImportsAreIncludes().set(project.provider(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    NativeToolChain toolChain = compile.getToolChain();
                    return Clang.class.isAssignableFrom(toolChain.getClass()) || Gcc.class.isAssignableFrom(toolChain.getClass());
                }
            }));
            compile.getDiscoveredInputs().set(discoverInputs.getDiscoveredInputs());
        }
    }
}
