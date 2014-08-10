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
package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.DefaultNativeExecutableBinarySpec;
import org.gradle.nativebinaries.internal.DefaultSharedLibraryBinarySpec;
import org.gradle.nativebinaries.internal.DefaultStaticLibraryBinarySpec;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.ToolChain;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder;

public class DefaultNativeBinariesFactory implements NativeBinariesFactory {
    private final Instantiator instantiator;
    private final Action<NativeBinarySpec> configureAction;
    private final NativeDependencyResolver resolver;

    public DefaultNativeBinariesFactory(Instantiator instantiator, Action<NativeBinarySpec> configureAction, NativeDependencyResolver resolver) {
        this.configureAction = configureAction;
        this.instantiator = instantiator;
        this.resolver = resolver;
    }

    public void createNativeBinaries(NativeComponentSpec component, BinaryNamingSchemeBuilder namingScheme, ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        if (component instanceof NativeLibrarySpec) {
            createNativeBinary(DefaultSharedLibraryBinarySpec.class, component, namingScheme.withTypeString("SharedLibrary").build(), toolChain, platform, buildType, flavor);
            createNativeBinary(DefaultStaticLibraryBinarySpec.class, component, namingScheme.withTypeString("StaticLibrary").build(), toolChain, platform, buildType, flavor);
        } else {
            createNativeBinary(DefaultNativeExecutableBinarySpec.class, component, namingScheme.withTypeString("Executable").build(), toolChain, platform, buildType, flavor);
        }
    }

    private void createNativeBinary(Class<? extends NativeBinarySpec> type, NativeComponentSpec component, BinaryNamingScheme namingScheme,
                            ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        NativeBinarySpec nativeBinary = instantiator.newInstance(type, component, flavor, toolChain, platform, buildType, namingScheme, resolver);
        setupDefaults(nativeBinary);
        component.getBinaries().add(nativeBinary);
    }

    private void setupDefaults(NativeBinarySpec nativeBinary) {
        configureAction.execute(nativeBinary);
    }
}
