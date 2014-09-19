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
import org.gradle.internal.reflect.Instantiator;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class PrebuiltLibraryInitializer implements Action<PrebuiltLibrary> {
    private final Instantiator instantiator;
    private final Set<NativePlatform> allPlatforms = new LinkedHashSet<NativePlatform>();
    private final Set<BuildType> allBuildTypes = new LinkedHashSet<BuildType>();
    private final Set<Flavor> allFlavors = new LinkedHashSet<Flavor>();

    public PrebuiltLibraryInitializer(Instantiator instantiator,
                                      Collection<? extends NativePlatform> allPlatforms, Collection<? extends BuildType> allBuildTypes, Collection<? extends Flavor> allFlavors) {
        this.instantiator = instantiator;
        this.allPlatforms.addAll(allPlatforms);
        this.allBuildTypes.addAll(allBuildTypes);
        this.allFlavors.addAll(allFlavors);
    }

    public void execute(PrebuiltLibrary prebuiltLibrary) {
        for (NativePlatform platform : allPlatforms) {
            for (BuildType buildType : allBuildTypes) {
                for (Flavor flavor : allFlavors) {
                    createNativeBinaries(prebuiltLibrary, platform, buildType, flavor);
                }
            }
        }
    }

    public void createNativeBinaries(PrebuiltLibrary library, NativePlatform platform, BuildType buildType, Flavor flavor) {
        createNativeBinary(DefaultPrebuiltSharedLibraryBinary.class, library, platform, buildType, flavor);
        createNativeBinary(DefaultPrebuiltStaticLibraryBinary.class, library, platform, buildType, flavor);
    }

    public <T extends NativeLibraryBinary> void createNativeBinary(Class<T> type, PrebuiltLibrary library, NativePlatform platform, BuildType buildType, Flavor flavor) {
        String name = getName(type, library, platform, buildType, flavor);
        T nativeBinary = instantiator.newInstance(type, name, library, buildType, platform, flavor);
        library.getBinaries().add(nativeBinary);
    }

    private <T extends NativeLibraryBinary> String getName(Class<T> type, PrebuiltLibrary library, NativePlatform platform, BuildType buildType, Flavor flavor) {
        BinaryNamingSchemeBuilder namingScheme = new DefaultBinaryNamingSchemeBuilder()
                .withComponentName(library.getName())
                .withTypeString(type.getSimpleName())
                .withVariantDimension(platform.getName())
                .withVariantDimension(buildType.getName())
                .withVariantDimension(flavor.getName());
        return namingScheme.build().getLifecycleTaskName();
    }
}
