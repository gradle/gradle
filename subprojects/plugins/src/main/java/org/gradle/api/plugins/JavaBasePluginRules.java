/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.java.DefaultJavaSourceSet;
import org.gradle.api.internal.java.DefaultJvmResourceSet;
import org.gradle.api.internal.jvm.ClassDirectoryBinarySpecInternal;
import org.gradle.api.internal.jvm.DefaultClassDirectoryBinarySpec;
import org.gradle.api.internal.project.taskfactory.TaskInstantiator;
import org.gradle.api.internal.tasks.SourceSetCompileClasspath;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.Classpath;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.platform.base.plugins.BinaryBasePlugin;

import javax.inject.Inject;
import java.util.List;

class JavaBasePluginRules implements Plugin<Project> {

    private final ModelRegistry modelRegistry;
    private final Instantiator instantiator;
    private final JavaToolChain javaToolChain;
    private final NamedEntityInstantiator<Task> taskInstantiator;
    private CollectionCallbackActionDecorator collectionCallbackActionDecorator;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;

    @Inject
    public JavaBasePluginRules(ModelRegistry modelRegistry, Instantiator instantiator, JavaToolChain javaToolChain, TaskInstantiator taskInstantiator, CollectionCallbackActionDecorator collectionCallbackActionDecorator, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        this.modelRegistry = modelRegistry;
        this.instantiator = instantiator;
        this.javaToolChain = javaToolChain;
        this.taskInstantiator = taskInstantiator;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(LanguageBasePlugin.class);
        project.getPluginManager().apply(BinaryBasePlugin.class);
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        BridgedBinaries binaries = collectBinariesForSourceSets(project.getTasks(), javaConvention);

        modelRegistry.register(ModelRegistrations.bridgedInstance(ModelReference.of("bridgedBinaries", BridgedBinaries.class), binaries)
            .descriptor("JavaBasePlugin.apply()")
            .hidden(true)
            .build());

    }

    private BridgedBinaries collectBinariesForSourceSets(final TaskContainer tasks, final JavaPluginConvention pluginConvention) {
        final Project project = pluginConvention.getProject();
        final List<ClassDirectoryBinarySpecInternal> binaries = Lists.newArrayList();
        pluginConvention.getSourceSets().all(new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {

                Provider<ProcessResources> resourcesTask = tasks.named(sourceSet.getProcessResourcesTaskName(), ProcessResources.class);
                Provider<JavaCompile> compileTask = tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);

                DefaultComponentSpecIdentifier binaryId = new DefaultComponentSpecIdentifier(project.getPath(), sourceSet.getName());
                ClassDirectoryBinarySpecInternal binary = instantiator.newInstance(DefaultClassDirectoryBinarySpec.class, binaryId, sourceSet, javaToolChain, DefaultJavaPlatform.current(), instantiator, taskInstantiator, collectionCallbackActionDecorator, domainObjectCollectionFactory);

                Classpath compileClasspath = new SourceSetCompileClasspath(sourceSet);
                DefaultJavaSourceSet javaSourceSet = instantiator.newInstance(DefaultJavaSourceSet.class, binaryId.child("java"), sourceSet.getJava(), compileClasspath);
                JvmResourceSet resourceSet = instantiator.newInstance(DefaultJvmResourceSet.class, binaryId.child("resources"), sourceSet.getResources());

                binary.addSourceSet(javaSourceSet);
                binary.addSourceSet(resourceSet);

                Provider<Task> classesTask = tasks.named(sourceSet.getClassesTaskName());
                attachTasksToBinary(binary, compileTask, resourcesTask, classesTask);
                binaries.add(binary);
            }
        });
        return new BridgedBinaries(binaries);
    }

    private void attachTasksToBinary(ClassDirectoryBinarySpecInternal binary, Provider<? extends Task> compileTask, Provider<? extends Task> resourcesTask, Provider<? extends Task> classesTask) {
        binary.getTasks().addLater(compileTask);
        binary.getTasks().addLater(resourcesTask);
        binary.getTasks().addLater(classesTask);
        binary.builtBy(classesTask);
    }

    static class Rules extends RuleSource {
        @Mutate
        void attachBridgedSourceSets(ProjectSourceSet projectSourceSet, BridgedBinaries bridgedBinaries) {
            for (ClassDirectoryBinarySpecInternal binary : bridgedBinaries.binaries) {
                projectSourceSet.addAll(binary.getInputs());
            }
        }

        @Mutate
        void attachBridgedBinaries(BinaryContainer binaries, BridgedBinaries bridgedBinaries) {
            for (BinarySpecInternal binary : bridgedBinaries.binaries) {
                binaries.put(binary.getProjectScopedName(), binary);
            }
        }
    }

    static class BridgedBinaries {
        final List<ClassDirectoryBinarySpecInternal> binaries;

        public BridgedBinaries(List<ClassDirectoryBinarySpecInternal> binaries) {
            this.binaries = binaries;
        }
    }

}
