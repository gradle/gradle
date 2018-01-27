/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.provider.Provider;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.swiftpm.Package;
import org.gradle.swiftpm.internal.AbstractProduct;
import org.gradle.swiftpm.internal.DefaultExecutableProduct;
import org.gradle.swiftpm.internal.DefaultLibraryProduct;
import org.gradle.swiftpm.internal.DefaultPackage;
import org.gradle.swiftpm.internal.Dependency;
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.internal.VcsMappingFactory;
import org.gradle.vcs.internal.VcsMappingInternal;
import org.gradle.vcs.internal.VcsMappingsStore;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A plugin that produces a Swift Package Manager manifests from the Gradle model.
 *
 * <p>This plugin should only be applied to the root project of a build.</p>
 *
 * @since 4.6
 */
@Incubating
public class SwiftPackageManagerExportPlugin implements Plugin<Project> {
    private final VcsMappingsStore vcsMappingsStore;
    private final VcsMappingFactory vcsMappingFactory;

    @Inject
    public SwiftPackageManagerExportPlugin(VcsMappingsStore vcsMappingsStore, VcsMappingFactory vcsMappingFactory) {
        this.vcsMappingsStore = vcsMappingsStore;
        this.vcsMappingFactory = vcsMappingFactory;
    }

    @Override
    public void apply(final Project project) {
        GenerateSwiftPackageManagerManifest manifestTask = project.getTasks().create("generateSwiftPmManifest", GenerateSwiftPackageManagerManifest.class);
        manifestTask.getManifestFile().set(project.getLayout().getProjectDirectory().file("Package.swift"));

        Provider<Package> products = project.getProviders().provider(new Callable<Package>() {
            @Override
            public Package call() {
                Set<AbstractProduct> products = new LinkedHashSet<AbstractProduct>();
                List<Dependency> dependencies = new ArrayList<Dependency>();
                for (Project p : project.getAllprojects()) {
                    for (CppApplication application : p.getComponents().withType(CppApplication.class)) {
                        DefaultExecutableProduct product = new DefaultExecutableProduct(p.getName(), application.getBaseName().get(), p.getProjectDir(), application.getCppSource());
                        collectDependencies(application.getImplementationDependencies(), dependencies, product);
                        // TODO - set header dir for applications
                        products.add(product);
                    }
                    for (CppLibrary library : p.getComponents().withType(CppLibrary.class)) {
                        DefaultLibraryProduct product = new DefaultLibraryProduct(p.getName(), library.getBaseName().get(), p.getProjectDir(), library.getCppSource());
                        collectDependencies(library.getImplementationDependencies(), dependencies, product);
                        Set<File> headerDirs = library.getPublicHeaderDirs().getFiles();
                        if (!headerDirs.isEmpty()) {
                            // TODO - deal with more than one directory
                            product.setPublicHeaderDir(headerDirs.iterator().next());
                        }
                        // TODO - linkage
                        products.add(product);
                    }
                    for (SwiftApplication application : p.getComponents().withType(SwiftApplication.class)) {
                        DefaultExecutableProduct product = new DefaultExecutableProduct(p.getName(), application.getModule().get(), p.getProjectDir(), application.getSwiftSource());
                        collectDependencies(application.getImplementationDependencies(), dependencies, product);
                        products.add(product);
                    }
                    for (SwiftLibrary library : p.getComponents().withType(SwiftLibrary.class)) {
                        DefaultLibraryProduct product = new DefaultLibraryProduct(p.getName(), library.getModule().get(), p.getProjectDir(), library.getSwiftSource());
                        collectDependencies(library.getImplementationDependencies(), dependencies, product);
                        // TODO - linkage
                        products.add(product);
                    }
                }
                return new DefaultPackage(products, dependencies);
            }
        });
        manifestTask.getPackage().set(products);
    }

    private void collectDependencies(Configuration configuration, Collection<Dependency> dependencies, AbstractProduct product) {
        // TODO - should use publication service to do this lookup, deal with ambiguous reference and caching of the mappings
        Action<VcsMapping> mappingRule = vcsMappingsStore.getVcsMappingRule();
        for (org.gradle.api.artifacts.Dependency dependency : configuration.getAllDependencies()) {
            if (dependency instanceof ProjectDependency) {
                ProjectDependency projectDependency = (ProjectDependency) dependency;
                for (SwiftLibrary library : projectDependency.getDependencyProject().getComponents().withType(SwiftLibrary.class)) {
                    product.getRequiredTargets().add(library.getModule().get());
                }
                for (CppLibrary library : projectDependency.getDependencyProject().getComponents().withType(CppComponent.class).withType(CppLibrary.class)) {
                    product.getRequiredTargets().add(library.getBaseName().get());
                }
            } else if (dependency instanceof ExternalModuleDependency) {
                ExternalModuleDependency externalDependency = (ExternalModuleDependency) dependency;
                VcsMappingInternal mapping = vcsMappingFactory.create(DefaultModuleComponentSelector.newSelector(externalDependency));
                mappingRule.execute(mapping);
                VersionControlSpec vcsSpec = mapping.getRepository();
                if (vcsSpec == null || !(vcsSpec instanceof GitVersionControlSpec)) {
                    continue;
                }
                // TODO - need to map version selector to Swift PM selector
                String versionSelector = externalDependency.getVersion();
                GitVersionControlSpec gitSpec = (GitVersionControlSpec) vcsSpec;
                dependencies.add(new Dependency(gitSpec.getUrl(), versionSelector));
                product.getRequiredProducts().add(externalDependency.getName());
            }
        }
    }
}
