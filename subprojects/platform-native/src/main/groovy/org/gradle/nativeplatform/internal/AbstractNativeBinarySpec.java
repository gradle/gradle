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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
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
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;

import java.io.File;
import java.util.*;

public abstract class AbstractNativeBinarySpec extends BaseBinarySpec implements NativeBinarySpecInternal {
    private final Set<? super Object> libs = new LinkedHashSet<Object>();
    private final Tool linker = new DefaultTool();
    private final Tool staticLibArchiver = new DefaultTool();

    // TODO:HH Use managed views for this, only applied when the respective language is applied
    private final Tool assembler = new DefaultTool();
    private final PreprocessingTool cCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool cppCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool objcCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool objcppCompiler = new DefaultPreprocessingTool();
    private final PreprocessingTool rcCompiler = new DefaultPreprocessingTool();
    private final Map<String, Tool> toolsByName = ImmutableMap.<String, Tool>builder()
            .put("assembler", assembler)
            .put("cCompiler", cCompiler)
            .put("cppCompiler", cppCompiler)
            .put("objcCompiler", objcCompiler)
            .put("objcppCompiler", objcppCompiler)
            .put("rcCompiler", rcCompiler)
            .build();

    private PlatformToolProvider toolProvider;
    private Flavor flavor;
    private NativeToolChain toolChain;
    private NativePlatform targetPlatform;
    private BuildType buildType;
    private NativeDependencyResolver resolver;
    private Map<File, PreCompiledHeader> prefixFileToPCH = Maps.newHashMap();

    @Override
    public String getDisplayName() {
        return getNamingScheme().getDescription();
    }

    @Override
    public NativeComponentSpec getComponent() {
        return getComponentAs(NativeComponentSpec.class);
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

    public Tool getAssembler() {
        return assembler;
    }

    public PreprocessingTool getcCompiler() {
        return cCompiler;
    }

    public PreprocessingTool getCppCompiler() {
        return cppCompiler;
    }

    public PreprocessingTool getObjcCompiler() {
        return objcCompiler;
    }

    public PreprocessingTool getObjcppCompiler() {
        return objcppCompiler;
    }

    public PreprocessingTool getRcCompiler() {
        return rcCompiler;
    }

    public Tool getToolByName(String name) {
        return toolsByName.get(name);
    }

    public Collection<NativeDependencySet> getLibs() {
        return resolve(getInputs().withType(DependentSourceSet.class)).getAllResults();
    }

    public Collection<NativeDependencySet> getLibs(DependentSourceSet sourceSet) {
        return resolve(Collections.singleton(sourceSet)).getAllResults();
    }

    public void lib(Object notation) {
        libs.add(notation);
    }

    public Collection<NativeLibraryBinary> getDependentBinaries() {
        return resolve(getInputs().withType(DependentSourceSet.class)).getAllLibraryBinaries();
    }

    public Map<File, PreCompiledHeader> getPrefixFileToPCH() {
        return prefixFileToPCH;
    }

    private NativeBinaryResolveResult resolve(Iterable<? extends DependentSourceSet> sourceSets) {
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
