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

package org.gradle.binaries.nativebinaries.internal;

import org.gradle.api.Project;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.binaries.nativebinaries.Flavor;
import org.gradle.binaries.nativebinaries.NativeComponent;
import org.gradle.binaries.nativebinaries.ToolChain;

import java.io.File;
import java.util.Collection;

class NativeBinaryFactory {
    private final Instantiator instantiator;
    private final Project project;
    private final boolean useToolChainDimension;

    public NativeBinaryFactory(Instantiator instantiator, Project project, Collection<? extends ToolChain> allToolChains) {
        this.instantiator = instantiator;
        this.project = project;
        this.useToolChainDimension = allToolChains.size() > 1;
    }

    public <T extends DefaultNativeBinary> T createNativeBinary(Class<T> type, NativeComponent component, ToolChain toolChain, Flavor flavor) {
        DefaultBinaryNamingScheme namingScheme = createNamingScheme(component, useToolChainDimension, toolChain, flavor);
        T nativeBinary = instantiator.newInstance(type, component, flavor, toolChain, namingScheme);
        setupDefaults(project, nativeBinary);
        component.getBinaries().add(nativeBinary);
        return nativeBinary;
    }

    private DefaultBinaryNamingScheme createNamingScheme(NativeComponent component, boolean useToolChainDimension, ToolChain toolChain, Flavor flavor) {
        DefaultBinaryNamingScheme namingScheme = new DefaultBinaryNamingScheme(component.getName());
        if (useToolChainDimension) {
            namingScheme = namingScheme.withVariantDimension(toolChain.getName());
        }
        if (component.getFlavors().size() > 1) {
            namingScheme = namingScheme.withVariantDimension(flavor.getName());
        }
        return namingScheme;
    }


    private void setupDefaults(Project project, DefaultNativeBinary nativeBinary) {
        nativeBinary.setOutputFile(new File(project.getBuildDir(), "binaries/" + nativeBinary.getNamingScheme().getOutputDirectoryBase() + "/" + nativeBinary.getOutputFileName()));
    }
}
