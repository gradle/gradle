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
package org.gradle.nativeplatform.internal.configure;

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;

public class DefaultNativeBinariesFactory implements NativeBinariesFactory {
    private final Action<NativeBinarySpec> configureAction;
    private final NativeDependencyResolver resolver;
    private final CollectionBuilder<NativeBinarySpec> binaries;

    public DefaultNativeBinariesFactory(CollectionBuilder<NativeBinarySpec> binaries, Action<NativeBinarySpec> configureAction, NativeDependencyResolver resolver) {
        this.binaries = binaries;
        this.configureAction = configureAction;
        this.resolver = resolver;
    }

    public void createNativeBinaries(NativeComponentSpec component, BinaryNamingSchemeBuilder namingScheme, NativeToolChain toolChain, PlatformToolProvider toolProvider, NativePlatform platform,
                                     BuildType buildType, Flavor flavor) {
        if (component instanceof NativeLibrarySpec) {
            createNativeBinary(SharedLibraryBinarySpec.class, component, namingScheme.withTypeString("SharedLibrary").build(), toolChain, toolProvider, platform, buildType, flavor);
            createNativeBinary(StaticLibraryBinarySpec.class, component, namingScheme.withTypeString("StaticLibrary").build(), toolChain, toolProvider, platform, buildType, flavor);
        } else {
            createNativeBinary(NativeExecutableBinarySpec.class, component, namingScheme.withTypeString("Executable").build(), toolChain, toolProvider, platform, buildType, flavor);
        }
    }

    private <T extends NativeBinarySpec> void createNativeBinary(Class<T> type, NativeComponentSpec component, BinaryNamingScheme namingScheme,
                                                                         NativeToolChain toolChain, PlatformToolProvider toolProvider, NativePlatform platform, BuildType buildType, Flavor flavor) {
        Action<NativeBinarySpec> initializer = binaryInitializer(configureAction, component, namingScheme, resolver, toolChain, toolProvider, platform, buildType, flavor);
        binaries.create(namingScheme.getLifecycleTaskName(), type, initializer);
    }

    public static Action<NativeBinarySpec> binaryInitializer(Action<? super NativeBinarySpec> configureAction, final NativeComponentSpec component, final BinaryNamingScheme namingScheme,
                                                             final NativeDependencyResolver resolver, final NativeToolChain toolChain, final PlatformToolProvider toolProvider,
                                                             final NativePlatform platform, final BuildType buildType, final Flavor flavor) {
        @SuppressWarnings("unchecked")
        Action<NativeBinarySpec> initializer = Actions.composite(new Action<NativeBinarySpec>() {
            @Override
            public void execute(NativeBinarySpec nativeBinarySpec) {
                NativeBinarySpecInternal nativeBinary = (NativeBinarySpecInternal) nativeBinarySpec;
                nativeBinary.setNamingScheme(namingScheme);
                nativeBinary.setComponent(component);
                nativeBinary.setTargetPlatform(platform);
                nativeBinary.setToolChain(toolChain);
                nativeBinary.setPlatformToolProvider(toolProvider);
                nativeBinary.setBuildType(buildType);
                nativeBinary.setFlavor(flavor);
                nativeBinary.setResolver(resolver);
            }
        }, configureAction);
        return initializer;
    }
}
