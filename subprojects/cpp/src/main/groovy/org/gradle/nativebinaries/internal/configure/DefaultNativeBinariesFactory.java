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
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.language.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.ProjectExecutableBinary;
import org.gradle.nativebinaries.internal.ProjectSharedLibraryBinary;
import org.gradle.nativebinaries.internal.ProjectStaticLibraryBinary;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.ToolChain;

class DefaultNativeBinariesFactory implements NativeBinariesFactory {
    private final Instantiator instantiator;
    private final Action<ProjectNativeBinary> configureAction;
    private final NativeDependencyResolver resolver;

    DefaultNativeBinariesFactory(Instantiator instantiator, Action<ProjectNativeBinary> configureAction, NativeDependencyResolver resolver) {
        this.configureAction = configureAction;
        this.instantiator = instantiator;
        this.resolver = resolver;
    }

    public void createNativeBinaries(ProjectNativeComponent component, BinaryNamingSchemeBuilder namingScheme, ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        if (component instanceof Library) {
            createNativeBinary(ProjectSharedLibraryBinary.class, component, namingScheme.withTypeString("SharedLibrary").build(), toolChain, platform, buildType, flavor);
            createNativeBinary(ProjectStaticLibraryBinary.class, component, namingScheme.withTypeString("StaticLibrary").build(), toolChain, platform, buildType, flavor);
        } else {
            createNativeBinary(ProjectExecutableBinary.class, component, namingScheme.withTypeString("Executable").build(), toolChain, platform, buildType, flavor);
        }
    }

    private void createNativeBinary(Class<? extends ProjectNativeBinary> type, ProjectNativeComponent component, BinaryNamingScheme namingScheme,
                            ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        ProjectNativeBinary nativeBinary = instantiator.newInstance(type, component, flavor, toolChain, platform, buildType, namingScheme, resolver);
        setupDefaults(nativeBinary);
        component.getBinaries().add(nativeBinary);
    }

    private void setupDefaults(ProjectNativeBinary nativeBinary) {
        configureAction.execute(nativeBinary);
    }
}
