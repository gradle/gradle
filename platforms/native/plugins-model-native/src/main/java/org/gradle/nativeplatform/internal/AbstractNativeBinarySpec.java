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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal;
import org.gradle.nativeplatform.internal.resolve.NativeBinaryRequirementResolveResult;
import org.gradle.nativeplatform.internal.resolve.NativeBinaryResolveResult;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("deprecation")
public abstract class AbstractNativeBinarySpec extends org.gradle.platform.base.binary.BaseBinarySpec implements NativeBinarySpecInternal {
    private final Set<? super Object> libs = new LinkedHashSet<Object>();
    private final org.gradle.nativeplatform.Tool linker = new DefaultTool();
    private final org.gradle.nativeplatform.Tool staticLibArchiver = new DefaultTool();

    // TODO:HH Use managed views for this, only applied when the respective language is applied
    private final org.gradle.nativeplatform.Tool assembler = new DefaultTool();
    private final org.gradle.nativeplatform.PreprocessingTool cCompiler = new DefaultPreprocessingTool();
    private final org.gradle.nativeplatform.PreprocessingTool cppCompiler = new DefaultPreprocessingTool();
    private final org.gradle.nativeplatform.PreprocessingTool objcCompiler = new DefaultPreprocessingTool();
    private final org.gradle.nativeplatform.PreprocessingTool objcppCompiler = new DefaultPreprocessingTool();
    private final org.gradle.nativeplatform.PreprocessingTool rcCompiler = new DefaultPreprocessingTool();
    private final Map<String, org.gradle.nativeplatform.Tool> toolsByName = ImmutableMap.<String, org.gradle.nativeplatform.Tool>builder()
            .put("assembler", assembler)
            .put("cCompiler", cCompiler)
            .put("cppCompiler", cppCompiler)
            .put("objcCompiler", objcCompiler)
            .put("objcppCompiler", objcppCompiler)
            .put("rcCompiler", rcCompiler)
            .build();

    private PlatformToolProvider toolProvider;
    private org.gradle.nativeplatform.Flavor flavor;
    private NativeToolChain toolChain;
    private NativePlatform targetPlatform;
    private org.gradle.nativeplatform.BuildType buildType;
    private NativeDependencyResolver resolver;
    private Map<File, PreCompiledHeader> prefixFileToPCH = new HashMap<>();
    private FileCollectionFactory fileCollectionFactory;

    @Override
    public String getDisplayName() {
        return getNamingScheme().getDescription();
    }

    @Override
    public org.gradle.nativeplatform.NativeComponentSpec getComponent() {
        return getComponentAs(org.gradle.nativeplatform.NativeComponentSpec.class);
    }

    @Override
    public org.gradle.nativeplatform.Flavor getFlavor() {
        return flavor;
    }

    @Override
    public void setFlavor(org.gradle.nativeplatform.Flavor flavor) {
        this.flavor = flavor;
    }

    @Override
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    @Override
    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = toolChain;
    }

    @Override
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    @Override
    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    @Override
    public org.gradle.nativeplatform.BuildType getBuildType() {
        return buildType;
    }

    @Override
    public void setBuildType(org.gradle.nativeplatform.BuildType buildType) {
        this.buildType = buildType;
    }

    @Override
    public org.gradle.nativeplatform.Tool getLinker() {
        return linker;
    }

    @Override
    public org.gradle.nativeplatform.Tool getStaticLibArchiver() {
        return staticLibArchiver;
    }

    @Override
    public org.gradle.nativeplatform.Tool getAssembler() {
        return assembler;
    }

    @Override
    public org.gradle.nativeplatform.PreprocessingTool getcCompiler() {
        return cCompiler;
    }

    @Override
    public org.gradle.nativeplatform.PreprocessingTool getCppCompiler() {
        return cppCompiler;
    }

    @Override
    public org.gradle.nativeplatform.PreprocessingTool getObjcCompiler() {
        return objcCompiler;
    }

    @Override
    public org.gradle.nativeplatform.PreprocessingTool getObjcppCompiler() {
        return objcppCompiler;
    }

    @Override
    public org.gradle.nativeplatform.PreprocessingTool getRcCompiler() {
        return rcCompiler;
    }

    @Override
    public org.gradle.nativeplatform.Tool getToolByName(String name) {
        return toolsByName.get(name);
    }

    @Override
    public Collection<org.gradle.nativeplatform.NativeDependencySet> getLibs() {
        return resolve(getInputs().withType(org.gradle.language.nativeplatform.DependentSourceSet.class)).getAllResults();
    }

    @Override
    public Collection<org.gradle.nativeplatform.NativeDependencySet> getLibs(org.gradle.language.nativeplatform.DependentSourceSet sourceSet) {
        return resolve(Collections.singleton(sourceSet)).getAllResults();
    }

    @Override
    public void lib(Object notation) {
        libs.add(notation);
    }

    @Override
    public Collection<org.gradle.nativeplatform.NativeLibraryBinary> getDependentBinaries() {
        return resolve(getInputs().withType(org.gradle.language.nativeplatform.DependentSourceSet.class)).getAllLibraryBinaries();
    }

    @Override
    public Collection<NativeBinaryRequirementResolveResult> getAllResolutions() {
        return resolve(getInputs().withType(org.gradle.language.nativeplatform.DependentSourceSet.class)).getAllResolutions();
    }

    @Override
    public Map<File, PreCompiledHeader> getPrefixFileToPCH() {
        return prefixFileToPCH;
    }

    @Override
    public void addPreCompiledHeaderFor(org.gradle.language.nativeplatform.DependentSourceSet sourceSet) {
        prefixFileToPCH.put(
            ((DependentSourceSetInternal)sourceSet).getPrefixHeaderFile(),
            new PreCompiledHeader(getIdentifier().child("pch")));
    }

    private NativeBinaryResolveResult resolve(Iterable<? extends org.gradle.language.nativeplatform.DependentSourceSet> sourceSets) {
        Set<? super Object> allLibs = new LinkedHashSet<Object>(libs);
        for (org.gradle.language.nativeplatform.DependentSourceSet dependentSourceSet : sourceSets) {
            allLibs.addAll(dependentSourceSet.getLibs());
        }
        NativeBinaryResolveResult resolution = new NativeBinaryResolveResult(this, allLibs);
        resolver.resolve(resolution);
        return resolution;
    }

    @Override
    public PlatformToolProvider getPlatformToolProvider() {
        return toolProvider;
    }

    @Override
    public void setPlatformToolProvider(PlatformToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    @Override
    public void setResolver(NativeDependencyResolver resolver) {
        this.resolver = resolver;
    }

    protected FileCollectionFactory getFileCollectionFactory() {
        return fileCollectionFactory;
    }

    @Override
    public void setFileCollectionFactory(FileCollectionFactory fileCollectionFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    protected BinaryBuildAbility getBinaryBuildAbility() {
        NativeToolChainInternal toolChainInternal = (NativeToolChainInternal) getToolChain();
        NativePlatformInternal platformInternal = (NativePlatformInternal) getTargetPlatform();
        return new ToolSearchBuildAbility(toolChainInternal.select(platformInternal));
    }

    @Override
    public void binaryInputs(FileCollection files) {
        // TODO - should split this up, so that the inputs are attached to an object that represents the binary, which is then later used to configure the link/assemble tasks
        getCreateOrLink().source(files);
    }

    protected abstract ObjectFilesToBinary getCreateOrLink();
}
