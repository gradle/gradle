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

package org.gradle.nativeplatform.internal.prebuilt;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class PrebuiltLibraryInitializer implements Action<PrebuiltLibrary> {
    private final Instantiator instantiator;
    private final FileCollectionFactory fileCollectionFactory;
    private final Set<NativePlatform> allPlatforms = new LinkedHashSet<NativePlatform>();
    private final Set<BuildType> allBuildTypes = new LinkedHashSet<BuildType>();
    private final Set<Flavor> allFlavors = new LinkedHashSet<Flavor>();

    public PrebuiltLibraryInitializer(Instantiator instantiator,
                                      FileCollectionFactory fileCollectionFactory,
                                      NativePlatforms nativePlatforms,
                                      Collection<? extends NativePlatform> allPlatforms,
                                      Collection<? extends BuildType> allBuildTypes,
                                      Collection<? extends Flavor> allFlavors) {
        this.instantiator = instantiator;
        this.fileCollectionFactory = fileCollectionFactory;
        this.allPlatforms.addAll(allPlatforms);
        this.allPlatforms.addAll(nativePlatforms.defaultPlatformDefinitions());
        this.allBuildTypes.addAll(allBuildTypes);
        this.allFlavors.addAll(allFlavors);
    }

    @Override
    public void execute(PrebuiltLibrary prebuiltLibrary) {
        for (NativePlatform platform : allPlatforms) {
            for (BuildType buildType : allBuildTypes) {
                for (Flavor flavor : allFlavors) {
                    createNativeBinaries(prebuiltLibrary, platform, buildType, flavor, fileCollectionFactory);
                }
            }
        }
    }

    public void createNativeBinaries(PrebuiltLibrary library, NativePlatform platform, BuildType buildType, Flavor flavor, FileCollectionFactory fileCollectionFactory) {
        createNativeBinary(DefaultPrebuiltSharedLibraryBinary.class, "shared", library, platform, buildType, flavor, fileCollectionFactory);
        createNativeBinary(DefaultPrebuiltStaticLibraryBinary.class, "static", library, platform, buildType, flavor, fileCollectionFactory);
    }

    public <T extends NativeLibraryBinary> void createNativeBinary(Class<T> type, String typeName, PrebuiltLibrary library, NativePlatform platform, BuildType buildType, Flavor flavor, FileCollectionFactory fileCollectionFactory) {
        String name = getName(typeName, library, platform, buildType, flavor);
        T nativeBinary = instantiator.newInstance(type, name, library, buildType, platform, flavor, fileCollectionFactory);
        library.getBinaries().add(nativeBinary);
    }

    private <T extends NativeLibraryBinary> String getName(String typeName, PrebuiltLibrary library, NativePlatform platform, BuildType buildType, Flavor flavor) {
        BinaryNamingScheme namingScheme = DefaultBinaryNamingScheme.component(library.getName())
                .withBinaryType(typeName)
                .withVariantDimension(platform.getName())
                .withVariantDimension(buildType.getName())
                .withVariantDimension(flavor.getName());
        return namingScheme.getBinaryName();
    }
}
