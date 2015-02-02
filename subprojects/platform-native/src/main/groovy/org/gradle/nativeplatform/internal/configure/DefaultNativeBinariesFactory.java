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
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.*;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;

public class DefaultNativeBinariesFactory implements NativeBinariesFactory {
    private final Instantiator instantiator;
    private final Action<NativeBinarySpec> configureAction;
    private final NativeDependencyResolver resolver;
    private final ITaskFactory taskFactory;

    public DefaultNativeBinariesFactory(Instantiator instantiator, Action<NativeBinarySpec> configureAction, NativeDependencyResolver resolver, ITaskFactory taskFactory) {
        this.configureAction = configureAction;
        this.instantiator = instantiator;
        this.resolver = resolver;
        this.taskFactory = taskFactory;
    }

    public void createNativeBinaries(NativeComponentSpec component, BinaryNamingSchemeBuilder namingScheme, NativeToolChain toolChain, PlatformToolProvider toolProvider, NativePlatform platform, BuildType buildType, Flavor flavor) {
        if (component instanceof NativeLibrarySpec) {
            createNativeBinary(DefaultSharedLibraryBinarySpec.class, component, namingScheme.withTypeString("SharedLibrary").build(), toolChain, toolProvider, platform, buildType, flavor);
            createNativeBinary(DefaultStaticLibraryBinarySpec.class, component, namingScheme.withTypeString("StaticLibrary").build(), toolChain, toolProvider, platform, buildType, flavor);
        } else {
            createNativeBinary(DefaultNativeExecutableBinarySpec.class, component, namingScheme.withTypeString("Executable").build(), toolChain, toolProvider, platform, buildType, flavor);
        }
    }
    private <T extends AbstractNativeBinarySpec> void createNativeBinary(Class<T> type, NativeComponentSpec component, BinaryNamingScheme namingScheme,
                                                                         NativeToolChain toolChain, PlatformToolProvider toolProvider, NativePlatform platform, BuildType buildType, Flavor flavor) {
        T nativeBinary = create(type, instantiator, component, namingScheme, resolver, toolChain, toolProvider, platform, buildType, flavor, taskFactory);
        setupDefaults(nativeBinary);
        component.getBinaries().add(nativeBinary);
    }

    public static <T extends AbstractNativeBinarySpec> T create(Class<T> type, Instantiator instantiator,
                                                                NativeComponentSpec component, BinaryNamingScheme namingScheme, NativeDependencyResolver resolver,
                                                                NativeToolChain toolChain, PlatformToolProvider toolProvider, NativePlatform platform, BuildType buildType, Flavor flavor, ITaskFactory taskFactory) {
        T nativeBinary = BaseBinarySpec.create(type, namingScheme.getLifecycleTaskName(), instantiator, taskFactory);
        nativeBinary.setNamingScheme(namingScheme);
        nativeBinary.setComponent(component);
        nativeBinary.setTargetPlatform(platform);
        nativeBinary.setToolChain(toolChain);
        nativeBinary.setPlatformToolProvider(toolProvider);
        nativeBinary.setBuildType(buildType);
        nativeBinary.setFlavor(flavor);
        nativeBinary.setResolver(resolver);
        return nativeBinary;
    }

    private void setupDefaults(NativeBinarySpec nativeBinary) {
        configureAction.execute(nativeBinary);
    }
}
