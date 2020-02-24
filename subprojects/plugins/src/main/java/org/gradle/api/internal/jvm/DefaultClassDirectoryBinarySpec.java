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
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.AbstractBuildableComponentSpec;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.ClassDirectoryBinarySpec;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection;
import org.gradle.platform.base.internal.FixedBuildAbility;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultClassDirectoryBinarySpec extends AbstractBuildableComponentSpec implements ClassDirectoryBinarySpecInternal {
    private final DomainObjectSet<LanguageSourceSet> sourceSets;
    private final SourceSet sourceSet;
    private final JavaToolChain toolChain;
    private final JavaPlatform platform;
    private final BinaryTasksCollection tasks;
    private boolean buildable = true;

    public DefaultClassDirectoryBinarySpec(ComponentSpecIdentifier componentIdentifier, SourceSet sourceSet, JavaToolChain toolChain, JavaPlatform platform, Instantiator instantiator, NamedEntityInstantiator<Task> taskInstantiator, CollectionCallbackActionDecorator collectionCallbackActionDecorator, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        super(componentIdentifier, ClassDirectoryBinarySpec.class);
        this.sourceSet = sourceSet;
        this.toolChain = toolChain;
        this.platform = platform;
        this.sourceSets = domainObjectCollectionFactory.newDomainObjectSet(LanguageSourceSet.class);
        this.tasks = instantiator.newInstance(DefaultBinaryTasksCollection.class, this, taskInstantiator, collectionCallbackActionDecorator);
    }

    @Override
    public LibraryBinaryIdentifier getId() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public ComponentSpec getComponent() {
        return null;
    }

    @Override
    public String getProjectScopedName() {
        return getName();
    }

    @Override
    public Class<? extends BinarySpec> getPublicType() {
        return ClassDirectoryBinarySpec.class;
    }

    @Override
    public BinaryTasksCollection getTasks() {
        return tasks;
    }

    @Override
    public JavaToolChain getToolChain() {
        return toolChain;
    }

    @Override
    public JavaPlatform getTargetPlatform() {
        return platform;
    }

    @Override
    public void setTargetPlatform(JavaPlatform platform) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setToolChain(JavaToolChain toolChain) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBuildable() {
        return getBuildAbility().isBuildable();
    }

    @Override
    public void setBuildable(boolean buildable) {
        this.buildable = buildable;
    }

    @Override
    public boolean isLegacyBinary() {
        return true;
    }

    @Override
    public File getClassesDir() {
        return sourceSet.getJava().getDestinationDirectory().getAsFile().get();
    }

    @Override
    public void setClassesDir(final File classesDir) {
        sourceSet.getJava().getDestinationDirectory().set(classesDir);
    }

    @Override
    public File getResourcesDir() {
        return sourceSet.getOutput().getResourcesDir();
    }

    @Override
    public void setResourcesDir(File resourcesDir) {
        sourceSet.getOutput().setResourcesDir(resourcesDir);
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
    public boolean hasCodependentSources() {
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

    @Override
    protected String getTypeName() {
        return "Classes";
    }

    @Override
    public BinaryBuildAbility getBuildAbility() {
        if (!buildable) {
            return new FixedBuildAbility(false);
        }
        return new ToolSearchBuildAbility(((JavaToolChainInternal) getToolChain()).select(getTargetPlatform()));
    }
}
