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

package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.internal.resolve.DefaultNativeDependencyResolver;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

class NativeBinaryFactory {
    private final Instantiator instantiator;
    private final ProjectInternal project;
    private final Set<Platform> allPlatforms = new LinkedHashSet<Platform>();
    private final Set<BuildType> allBuildTypes = new LinkedHashSet<BuildType>();
    private final Set<Flavor> allFlavors = new LinkedHashSet<Flavor>();

    public NativeBinaryFactory(Instantiator instantiator, ProjectInternal project, Collection<? extends Platform> allPlatforms,
                               Collection<? extends BuildType> allBuildTypes, Collection<? extends Flavor> allFlavors) {
        this.instantiator = instantiator;
        this.project = project;
        this.allPlatforms.addAll(allPlatforms);
        this.allBuildTypes.addAll(allBuildTypes);
        this.allFlavors.addAll(allFlavors);
    }

    public Collection<NativeBinary> createNativeBinaries(NativeComponent component, ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        Collection<NativeBinary> binaries = new LinkedList<NativeBinary>();
        if (component instanceof Library) {
            binaries.add(createNativeBinary(DefaultSharedLibraryBinary.class, component, toolChain, platform, buildType, flavor));
            binaries.add(createNativeBinary(DefaultStaticLibraryBinary.class, component, toolChain, platform, buildType, flavor));
        } else {
            binaries.add(createNativeBinary(DefaultExecutableBinary.class, component, toolChain, platform, buildType, flavor));
        }
        return binaries;
    }

    public <T extends DefaultNativeBinary> T createNativeBinary(Class<T> type, NativeComponent component, ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        NativeDependencyResolver resolver = new DefaultNativeDependencyResolver(project);
        DefaultBinaryNamingScheme namingScheme = createNamingScheme(component, platform, buildType, flavor);
        T nativeBinary = instantiator.newInstance(type, component, flavor, toolChain, platform, buildType, namingScheme, resolver);
        setupDefaults(project, nativeBinary);
        component.getBinaries().add(nativeBinary);
        return nativeBinary;
    }

    private DefaultBinaryNamingScheme createNamingScheme(NativeComponent component, Platform platform, BuildType buildType, Flavor flavor) {
        DefaultBinaryNamingScheme namingScheme = new DefaultBinaryNamingScheme(component.getName());
        if (usePlatformDimension(component)) {
            namingScheme = namingScheme.withVariantDimension(platform.getName());
        }
        if (useBuildTypeDimension(component)) {
            namingScheme = namingScheme.withVariantDimension(buildType.getName());
        }
        if (useFlavorDimension(component)) {
            namingScheme = namingScheme.withVariantDimension(flavor.getName());
        }
        return namingScheme;
    }

    private boolean usePlatformDimension(NativeComponent component) {
        return ((NativeComponentInternal) component).choosePlatforms(allPlatforms).size() > 1;
    }

    private boolean useBuildTypeDimension(NativeComponent component) {
        return ((NativeComponentInternal) component).chooseBuildTypes(allBuildTypes).size() > 1;
    }

    private boolean useFlavorDimension(NativeComponent component) {
        return ((NativeComponentInternal) component).chooseFlavors(allFlavors).size() > 1;
    }

    private void setupDefaults(Project project, DefaultNativeBinary nativeBinary) {
        nativeBinary.setOutputFile(new File(project.getBuildDir(), "binaries/" + nativeBinary.getNamingScheme().getOutputDirectoryBase() + "/" + nativeBinary.getOutputFileName()));
    }
}
