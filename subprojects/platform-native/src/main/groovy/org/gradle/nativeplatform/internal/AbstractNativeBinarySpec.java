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

import org.gradle.api.file.FileCollection;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.resolve.NativeBinaryResolveResult;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractNativeBinarySpec extends BaseBinarySpec implements NativeBinarySpecInternal {
    private final Set<? super Object> libs = new LinkedHashSet<Object>();
    private final DefaultTool linker = new DefaultTool();
    private final DefaultTool staticLibArchiver = new DefaultTool();
    private NativeComponentSpec component;
    private PlatformToolProvider toolProvider;
    private BinaryNamingScheme namingScheme;
    private Flavor flavor;
    private NativeToolChain toolChain;
    private NativePlatform targetPlatform;
    private BuildType buildType;
    private NativeDependencyResolver resolver;

    public String getDisplayName() {
        return namingScheme.getDescription();
    }

    public NativeComponentSpec getComponent() {
        return component;
    }

    public void setComponent(NativeComponentSpec component) {
        this.component = component;
        setBinarySources(((ComponentSpecInternal) component).getSources().copy(getName()));
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public void setFlavor(Flavor flavor) {
        this.flavor = flavor;
    }

    public NativeToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = toolChain;
    }

    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public BuildType getBuildType() {
        return buildType;
    }

    public void setBuildType(BuildType buildType) {
        this.buildType = buildType;
    }

    public Tool getLinker() {
        return linker;
    }

    public Tool getStaticLibArchiver() {
        return staticLibArchiver;
    }

    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }

    public void setNamingScheme(BinaryNamingScheme namingScheme) {
        this.namingScheme = namingScheme;
    }

    public Collection<NativeDependencySet> getLibs() {
        return resolve(getSource().withType(DependentSourceSet.class)).getAllResults();
    }

    public Collection<NativeDependencySet> getLibs(DependentSourceSet sourceSet) {
        return resolve(Collections.singleton(sourceSet)).getAllResults();
    }

    public void lib(Object notation) {
        libs.add(notation);
    }

    public Collection<NativeLibraryBinary> getDependentBinaries() {
        return resolve(getSource().withType(DependentSourceSet.class)).getAllLibraryBinaries();
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

    public void setPlatformToolProvider(PlatformToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    public void setResolver(NativeDependencyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected BinaryBuildAbility getBinaryBuildAbility() {
        NativeToolChainInternal toolChainInternal = (NativeToolChainInternal) getToolChain();
        NativePlatformInternal platformInternal = (NativePlatformInternal) getTargetPlatform();
        return new ToolSearchBuildAbility(toolChainInternal.select(platformInternal));
    }

    public void binaryInputs(FileCollection files) {
        // TODO - should split this up, so that the inputs are attached to an object that represents the binary, which is then later used to configure the link/assemble tasks
        getCreateOrLink().source(files);
    }

    protected abstract ObjectFilesToBinary getCreateOrLink();
}
