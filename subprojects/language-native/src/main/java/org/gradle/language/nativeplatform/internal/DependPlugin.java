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

package org.gradle.language.nativeplatform.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.tasks.CCompile;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.language.nativeplatform.tasks.Depend;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.platform.base.BinaryTasks;

import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Creates depend tasks for each supported native compile task.
 *
 * Currently, {@link CCompile} and {@link CppCompile} are supported.
 *
 * @since 4.3
 */
@Incubating
@NonNullApi
public class DependPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getComponents().withType(CppBinary.class, new Action<CppBinary>() {
            @Override
            public void execute(CppBinary binary) {
                final Names names = Names.of(binary.getName());
                final String language = "cpp";
                project.getTasks().matching(new Spec<Task>() {
                    @Override
                    public boolean isSatisfiedBy(Task element) {
                        return element.getName().equals(names.getCompileTaskName(language));
                    }
                }).all(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        createDependTask(project, names.withSuffix(language), (AbstractNativeCompileTask) task);
                    }
                });
            }
        });
    }

    static class Rules extends RuleSource {
        @BinaryTasks
        void addDependTask(final ModelMap<Task> tasks, final NativeBinarySpecInternal binary) {
            CreateDependTaskAction createDependTaskAction = new CreateDependTaskAction(binary, tasks);
            binary.getInputs().withType(CppSourceSet.class, createDependTaskAction);
            binary.getInputs().withType(CSourceSet.class, createDependTaskAction);
        }
    }

    private static class CreateDependTaskAction implements Action<LanguageSourceSet> {
        private final NativeBinarySpecInternal binary;
        private final ModelMap<Task> tasks;

        public CreateDependTaskAction(NativeBinarySpecInternal binary, ModelMap<Task> tasks) {
            this.binary = binary;
            this.tasks = tasks;
        }

        @Override
        public void execute(LanguageSourceSet cppSourceSet) {
            final String suffix = capitalize(binary.getProjectScopedName()) + capitalize(((LanguageSourceSetInternal) cppSourceSet).getProjectScopedName());
            tasks.named("compile" + suffix, new Action<Task>() {
                @Override
                public void execute(Task task) {
                    final AbstractNativeCompileTask compileTask = (AbstractNativeCompileTask) task;
                    // When I create the task by using `tasks` CppLanguageIncrementalBuildIntegrationTest."recompiles but does not relink executable with source comment change" fails
                    final Project project = compileTask.getProject();
                    project.getTasks().create("depend" + suffix, Depend.class, new Action<Depend>() {
                        @Override
                        public void execute(Depend depend) {
                            configureDependTask(project, compileTask, depend);
                        }
                    });
                }
            });
        }
    }

    private static void createDependTask(Project project, String name, final AbstractNativeCompileTask compile) {
        Depend discoverInputs = project.getTasks().create("depend" + StringUtils.capitalize(name), Depend.class);
        configureDependTask(project, compile, discoverInputs);
    }

    private static void configureDependTask(Project project, final AbstractNativeCompileTask compile, Depend depend) {
        depend.source(compile.getSource());
        depend.includes(compile.getIncludes());
        depend.getHeaderDependenciesFile().set(project.getLayout().getBuildDirectory().file(depend.getName() + "/" + "inputs.txt"));
        depend.getImportsAreIncludes().set(project.provider(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                NativeToolChain toolChain = compile.getToolChain();
                return Clang.class.isAssignableFrom(toolChain.getClass()) || Gcc.class.isAssignableFrom(toolChain.getClass());
            }
        }));
        depend.getToolChain().set(project.provider(new Callable<NativeToolChain>() {
            @Override
            public NativeToolChain call() throws Exception {
                return compile.getToolChain();
            }
        }));
        depend.getTargetPlatform().set(project.provider(new Callable<NativePlatform>() {
            @Override
            public NativePlatform call() throws Exception {
                return compile.getTargetPlatform();
            }
        }));
        depend.getSpec().set(project.provider(new Callable<NativeCompileSpec>() {
            @Override
            public NativeCompileSpec call() throws Exception {
                return compile.createCompileSpec();
            }
        }));
        compile.getHeaderDependenciesFile().set(depend.getHeaderDependenciesFile());
    }
}
