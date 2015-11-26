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
package org.gradle.api.internal.jvm;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.ClassDirectoryBinarySpec;
import org.gradle.jvm.JvmBinaryTasks;
import org.gradle.jvm.internal.DefaultJvmBinaryTasks;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.*;

import java.io.File;

@SuppressWarnings("deprecation")
public class DefaultClassDirectoryBinarySpec extends AbstractBuildableModelElement implements ClassDirectoryBinarySpecInternal {
    private final DefaultDomainObjectSet<LanguageSourceSet> sourceSets = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
    private final String name;
    private final SourceSet sourceSet;
    private final JavaToolChain toolChain;
    private final JavaPlatform platform;
    private final DefaultJvmBinaryTasks tasks;
    private boolean buildable = true;

    public DefaultClassDirectoryBinarySpec(String name, SourceSet sourceSet, JavaToolChain toolChain, JavaPlatform platform, Instantiator instantiator, ITaskFactory taskFactory) {
        this.name = name;
        this.sourceSet = sourceSet;
        this.toolChain = toolChain;
        this.platform = platform;
        this.tasks = instantiator.newInstance(DefaultJvmBinaryTasks.class, new DefaultBinaryTasksCollection(this, taskFactory));
    }

    private String removeClassesSuffix(String name) {
        if (name.endsWith("Classes")) {
            return name.substring(0, name.length() - 7);
        }
        return name;
    }

    @Override
    public String getProjectScopedName() {
        return getName();
    }

    @Override
    public Class<? extends BinarySpec> getPublicType() {
        return ClassDirectoryBinarySpec.class;
    }

    public JvmBinaryTasks getTasks() {
        return tasks;
    }

    public JavaToolChain getToolChain() {
        return toolChain;
    }

    public JavaPlatform getTargetPlatform() {
        return platform;
    }

    public void setTargetPlatform(JavaPlatform platform) {
        throw new UnsupportedOperationException();
    }

    public void setToolChain(JavaToolChain toolChain) {
        throw new UnsupportedOperationException();
    }

    public boolean isBuildable() {
        return getBuildAbility().isBuildable();
    }

    public void setBuildable(boolean buildable) {
        this.buildable = buildable;
    }

    public boolean isLegacyBinary() {
        return true;
    }

    public String getName() {
        return name;
    }

    public File getClassesDir() {
        return sourceSet.getOutput().getClassesDir();
    }

    public void setClassesDir(File classesDir) {
        sourceSet.getOutput().setClassesDir(classesDir);
    }

    public File getResourcesDir() {
        return sourceSet.getOutput().getResourcesDir();
    }

    public void setResourcesDir(File resourcesDir) {
        sourceSet.getOutput().setResourcesDir(resourcesDir);
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getSource() {
        return getInputs();
    }

    @Override
    public ModelMap<LanguageSourceSet> getSources() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BinaryNamingScheme getNamingScheme() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNamingScheme(BinaryNamingScheme namingScheme) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getInputs() {
        return sourceSets;
    }

    @Override
    public void addSourceSet(LanguageSourceSet sourceSet) {
        sourceSets.add(sourceSet);
    }

    public String getDisplayName() {
        return "classes '" + removeClassesSuffix(name) + "'";
    }

    public String toString() {
        return getDisplayName();
    }

    @Override
    public BinaryBuildAbility getBuildAbility() {
        if (!buildable) {
            return new FixedBuildAbility(false);
        }
        return new ToolSearchBuildAbility(((JavaToolChainInternal) getToolChain()).select(getTargetPlatform()));
    }
}
