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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.Variant;

import java.util.Collection;

/**
 * Represents a binary artifact that is the result of building a native component.
 *
 * @deprecated The Gradle software model is deprecated and will be removed in Gradle 10. Use the new component model (e.g. {@code cpp-application}, {@code cpp-library}, {@code swift-application}, {@code swift-library}, {@code xctest}) instead.
 * @since 2.2
 */
@Incubating @HasInternalProtocol
@Deprecated
public interface NativeBinarySpec extends BinarySpec {
    /**
     * The component that this binary was built from.
     * @since 2.2
     */
    NativeComponentSpec getComponent();

    /**
     * The {@link org.gradle.nativeplatform.Flavor} that this binary was built with.
     * @since 2.2
     */
    @Variant
    Flavor getFlavor();

    /**
     * Returns the {@link org.gradle.nativeplatform.platform.NativePlatform} that this binary is targeted to run on.
     * @since 2.2
     */
    @Variant
    NativePlatform getTargetPlatform();

    /**
     * Returns the {@link org.gradle.nativeplatform.BuildType} used to construct this binary.
     * @since 2.2
     */
    @Variant
    BuildType getBuildType();

    /**
     * The libraries that should be linked into this binary.
     * @since 2.2
     */
    Collection<NativeDependencySet> getLibs();

    /**
     * Adds a library as input to this binary.
     * <p>
     * This method accepts the following types:
     *
     * <ul>
     *     <li>A {@link NativeLibrarySpec}</li>
     *     <li>A {@link NativeDependencySet}</li>
     *     <li>A {@link java.util.Map} containing the library selector.</li>
     * </ul>
     *
     * The Map notation supports the following String attributes:
     *
     * <ul>
     *     <li>project: the path to the project containing the library (optional, defaults to current project)</li>
     *     <li>library: the name of the library (required)</li>
     *     <li>linkage: the library linkage required ['shared'/'static'] (optional, defaults to 'shared')</li>
     * </ul>
     * @since 2.2
     */
    void lib(Object library);

    /**
     * Returns the {@link org.gradle.nativeplatform.toolchain.NativeToolChain} that will be used to build this binary.
     * @since 2.2
     */
    NativeToolChain getToolChain();

    // TODO It would be better if these were added via a separate managed view, rather than hard coded.
    /**
     * The configuration of the linker used when linking this binary.
     *
     * Valid for {@link SharedLibraryBinarySpec} and {@link NativeExecutableBinarySpec}.
     * @since 2.2
     */
    Tool getLinker();

    /**
     * The configuration of the static library archiver used when creating this binary.
     *
     * Valid for {@link StaticLibraryBinarySpec}.
     * @since 2.2
     */
    Tool getStaticLibArchiver();

    /**
     * The configuration of the assembler used when compiling assembly sources this binary.
     *
     * Valid for {@link SharedLibraryBinarySpec}, {@link StaticLibraryBinarySpec} and
     * {@link NativeExecutableBinarySpec} when the 'assembler' plugin is applied.
     * @since 2.10
     */
    Tool getAssembler();

    /**
     * The configuration of the C compiler used when compiling C sources for this binary.
     *
     * Valid for {@link SharedLibraryBinarySpec}, {@link StaticLibraryBinarySpec} and
     * {@link NativeExecutableBinarySpec} when the 'c' plugin is applied.
     * @since 2.10
     */
    PreprocessingTool getcCompiler();

    /**
     * The configuration of the C++ compiler used when compiling C++ sources for this binary.
     *
     * Valid for {@link SharedLibraryBinarySpec}, {@link StaticLibraryBinarySpec} and
     * {@link NativeExecutableBinarySpec} when the 'cpp' plugin is applied.
     * @since 2.10
     */
    PreprocessingTool getCppCompiler();

    /**
     * The configuration of the Objective-C compiler used when compiling Objective-C sources for this binary.
     *
     * Valid for {@link SharedLibraryBinarySpec}, {@link StaticLibraryBinarySpec} and
     * {@link NativeExecutableBinarySpec} when the 'objective-c' plugin is applied.
     * @since 2.10
     */
    PreprocessingTool getObjcCompiler();

    /**
     * The configuration of the Objective-C++ compiler used when compiling Objective-C++ sources for this binary.
     *
     * Valid for {@link SharedLibraryBinarySpec}, {@link StaticLibraryBinarySpec} and
     * {@link NativeExecutableBinarySpec} when the 'objective-cpp' plugin is applied.
     * @since 2.10
     */
    PreprocessingTool getObjcppCompiler();

    /**
     * The configuration of the Resource compiler used when compiling resources for this binary.
     *
     * Valid for {@link SharedLibraryBinarySpec}, {@link StaticLibraryBinarySpec} and
     * {@link NativeExecutableBinarySpec} when the 'windows-resources' plugin is applied.
     * @since 2.10
     */
    PreprocessingTool getRcCompiler();
}
