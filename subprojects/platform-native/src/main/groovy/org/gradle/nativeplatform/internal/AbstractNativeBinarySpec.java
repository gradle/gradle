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

package org.gradle.nativeplatform.internal;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetContainer;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.resolve.NativeBinaryResolveResult;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractNativeBinarySpec extends AbstractBuildableModelElement implements NativeBinarySpecInternal {
    private final NativeComponentSpec component;
    private final LanguageSourceSetContainer sourceSets = new LanguageSourceSetContainer();
    private final Set<? super Object> libs = new LinkedHashSet<Object>();
    private final DefaultTool linker = new DefaultTool();
    private final DefaultTool staticLibArchiver = new DefaultTool();
    private final NativeBinaryTasks tasks = new DefaultNativeBinaryTasks(this);
    private final PlatformToolProvider toolProvider;
    private final BinaryNamingScheme namingScheme;
    private final Flavor flavor;
    private final NativeToolChain toolChain;
    private final NativePlatform targetPlatform;
    private final BuildType buildType;
    private final NativeDependencyResolver resolver;
    private boolean buildable;

    protected AbstractNativeBinarySpec(NativeComponentSpec owner, Flavor flavor, NativeToolChain toolChain, PlatformToolProvider toolProvider, NativePlatform targetPlatform,
                                       BuildType buildType, BinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        this.component = owner;
        this.toolProvider = toolProvider;
        this.namingScheme = namingScheme;
        this.flavor = flavor;
        this.toolChain = toolChain;
        this.targetPlatform = targetPlatform;
        this.buildType = buildType;
        this.buildable = true;
        this.resolver = resolver;
        component.getSource().all(new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet sourceSet) {
                sourceSets.add(sourceSet);
            }
        });
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return namingScheme.getDescription();
    }

    public String getName() {
        return namingScheme.getLifecycleTaskName();
    }

    public NativeComponentSpec getComponent() {
        return component;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public NativeToolChain getToolChain() {
        return toolChain;
    }

    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public BuildType getBuildType() {
        return buildType;
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return sourceSets;
    }

    public void source(Object sources) {
        sourceSets.source(sources);
    }

    public Tool getLinker() {
        return linker;
    }

    public Tool getStaticLibArchiver() {
        return staticLibArchiver;
    }

    public NativeBinaryTasks getTasks() {
        return tasks;
    }

    public NativeBinaryTasks getNativeBinaryTasks() {
        return getTasks();
    }

    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }

    public Collection<NativeDependencySet> getLibs() {
        return resolve(sourceSets.withType(DependentSourceSet.class)).getAllResults();
    }

    public Collection<NativeDependencySet> getLibs(DependentSourceSet sourceSet) {
        return resolve(Collections.singleton(sourceSet)).getAllResults();
    }

    public void lib(Object notation) {
        libs.add(notation);
    }

    public Collection<NativeLibraryBinary> getDependentBinaries() {
        return resolve(sourceSets.withType(DependentSourceSet.class)).getAllLibraryBinaries();
    }

    private NativeBinaryResolveResult resolve(Collection<? extends DependentSourceSet> sourceSets) {
        Set<? super Object> allLibs = new LinkedHashSet<Object>(libs);
        for (DependentSourceSet dependentSourceSet : sourceSets) {
            allLibs.addAll(dependentSourceSet.getLibs());
        }
        NativeBinaryResolveResult resolution = new NativeBinaryResolveResult(this, allLibs);
        resolver.resolve(resolution);
        return resolution;
    }

    public PlatformToolProvider getPlatformToolProvider() {
        return toolProvider;
    }

    public boolean isBuildable() {
        return buildable;
    }

    public void setBuildable(boolean buildable) {
        this.buildable = buildable;
    }

    public boolean isLegacyBinary() {
        return false;
    }


}
