/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativecode.base.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.ReflectiveNamedDomainObjectFactory;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinariesContainer;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.nativecode.base.*;
import org.gradle.nativecode.base.internal.*;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * temp plugin, not sure what will provide the binaries container and model elements
 */
@Incubating
public class BinariesPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;
    private final ProjectConfigurationActionContainer configurationActions;

    @Inject
    public BinariesPlugin(Instantiator instantiator, ProjectConfigurationActionContainer configurationActions) {
        this.instantiator = instantiator;
        this.configurationActions = configurationActions;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(LanguageBasePlugin.class);
        final BinariesContainer binaries = project.getExtensions().getByType(BinariesContainer.class);

        final ToolChainRegistry toolChains = project.getExtensions().create("compilers",
                DefaultToolChainRegistry.class,
                instantiator
        );
        final NamedDomainObjectSet<Executable> executables = project.getExtensions().create(
                "executables",
                FactoryNamedDomainObjectContainer.class,
                Executable.class,
                instantiator,
                new ReflectiveNamedDomainObjectFactory<Executable>(DefaultExecutable.class)
        );

        final NamedDomainObjectSet<Library> libraries = project.getExtensions().create("libraries",
                FactoryNamedDomainObjectContainer.class,
                Library.class,
                instantiator,
                new ReflectiveNamedDomainObjectFactory<Library>(DefaultLibrary.class, project.getFileResolver())
        );

        configurationActions.add(new Action<ProjectInternal>() {
            public void execute(ProjectInternal projectInternal) {
                ToolChain defaultToolChain = toolChains.getDefaultToolChain();
                for (Library library : libraries) {
                    DefaultSharedLibraryBinary sharedLibraryBinary = instantiator.newInstance(DefaultSharedLibraryBinary.class, library, defaultToolChain);
                    register(setupDefaults(project, sharedLibraryBinary), library, binaries);
                    register(setupDefaults(project, instantiator.newInstance(DefaultStaticLibraryBinary.class, library, defaultToolChain)), library, binaries);
                }
                for (Executable executable : executables) {
                    register(setupDefaults(project, instantiator.newInstance(DefaultExecutableBinary.class, executable, defaultToolChain)), executable, binaries);
                }
            }
        });
    }

    private void register(NativeBinary binary, NativeComponent component, BinariesContainer binariesContainer) {
        component.getBinaries().add(binary);
        binariesContainer.add(binary);
    }

    private NativeBinary setupDefaults(final ProjectInternal project, final DefaultNativeBinary nativeBinary) {
        new DslObject(nativeBinary).getConventionMapping().map("outputFile", new Callable<File>() {
            public File call() throws Exception {
                return new File(project.getBuildDir(), "binaries/" + nativeBinary.getNamingScheme().getOutputDirectoryBase() + "/" + nativeBinary.getOutputFileName());
            }
        });
        return nativeBinary;
    }

}