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
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("deprecation")
public class PrebuiltLibraryInitializer implements Action<org.gradle.nativeplatform.PrebuiltLibrary> {
    private final Instantiator instantiator;
    private final FileCollectionFactory fileCollectionFactory;
    private final Set<NativePlatform> allPlatforms = new LinkedHashSet<NativePlatform>();
    private final Set<org.gradle.nativeplatform.BuildType> allBuildTypes = new LinkedHashSet<org.gradle.nativeplatform.BuildType>();
    private final Set<org.gradle.nativeplatform.Flavor> allFlavors = new LinkedHashSet<org.gradle.nativeplatform.Flavor>();

    public PrebuiltLibraryInitializer(Instantiator instantiator,
                                      FileCollectionFactory fileCollectionFactory,
                                      NativePlatforms nativePlatforms,
                                      Collection<? extends NativePlatform> allPlatforms,
                                      Collection<? extends org.gradle.nativeplatform.BuildType> allBuildTypes,
                                      Collection<? extends org.gradle.nativeplatform.Flavor> allFlavors) {
        this.instantiator = instantiator;
        this.fileCollectionFactory = fileCollectionFactory;
        this.allPlatforms.addAll(allPlatforms);
        this.allPlatforms.addAll(nativePlatforms.defaultPlatformDefinitions());
        this.allBuildTypes.addAll(allBuildTypes);
        this.allFlavors.addAll(allFlavors);
    }

    @Override
    public void execute(org.gradle.nativeplatform.PrebuiltLibrary prebuiltLibrary) {
        for (NativePlatform platform : allPlatforms) {
            for (org.gradle.nativeplatform.BuildType buildType : allBuildTypes) {
                for (org.gradle.nativeplatform.Flavor flavor : allFlavors) {
                    createNativeBinaries(prebuiltLibrary, platform, buildType, flavor, fileCollectionFactory);
                }
            }
        }
    }

    public void createNativeBinaries(org.gradle.nativeplatform.PrebuiltLibrary library, NativePlatform platform, org.gradle.nativeplatform.BuildType buildType, org.gradle.nativeplatform.Flavor flavor, FileCollectionFactory fileCollectionFactory) {
        createNativeBinary(DefaultPrebuiltSharedLibraryBinary.class, "shared", library, platform, buildType, flavor, fileCollectionFactory);
        createNativeBinary(DefaultPrebuiltStaticLibraryBinary.class, "static", library, platform, buildType, flavor, fileCollectionFactory);
    }

    public <T extends org.gradle.nativeplatform.NativeLibraryBinary> void createNativeBinary(Class<T> type, String typeName, org.gradle.nativeplatform.PrebuiltLibrary library, NativePlatform platform, org.gradle.nativeplatform.BuildType buildType, org.gradle.nativeplatform.Flavor flavor, FileCollectionFactory fileCollectionFactory) {
        String name = getName(typeName, library, platform, buildType, flavor);
        T nativeBinary = instantiator.newInstance(type, name, library, buildType, platform, flavor, fileCollectionFactory);
        library.getBinaries().add(nativeBinary);
    }

    private String getName(String typeName, org.gradle.nativeplatform.PrebuiltLibrary library, NativePlatform platform, org.gradle.nativeplatform.BuildType buildType, org.gradle.nativeplatform.Flavor flavor) {
        BinaryNamingScheme namingScheme = DefaultBinaryNamingScheme.component(library.getName())
                .withBinaryType(typeName)
                .withVariantDimension(platform.getName())
                .withVariantDimension(buildType.getName())
                .withVariantDimension(flavor.getName());
        return namingScheme.getBinaryName();
    }
}
