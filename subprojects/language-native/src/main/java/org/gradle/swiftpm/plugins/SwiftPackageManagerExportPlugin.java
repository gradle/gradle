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

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.swiftpm.Product;
import org.gradle.swiftpm.internal.DefaultExecutableProduct;
import org.gradle.swiftpm.internal.DefaultLibraryProduct;
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class SwiftPackageManagerExportPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        GenerateSwiftPackageManagerManifest manifestTask = project.getTasks().create("generateSwiftPmManifest", GenerateSwiftPackageManagerManifest.class);
        manifestTask.getManifestFile().set(project.getLayout().getProjectDirectory().file("Package.swift"));

        Provider<Set<Product>> products = project.getProviders().provider(new Callable<Set<Product>>() {
            @Override
            public Set<Product> call() {
                Set<Product> result = new LinkedHashSet<Product>();
                for (Project p : project.getAllprojects()) {
                    for (CppApplication application : p.getComponents().withType(CppApplication.class)) {
                        result.add(new DefaultExecutableProduct(application.getBaseName().get(), p.getProjectDir(), application.getCppSource(), mapDependencies(application.getImplementationDependencies())));
                    }
                    for (CppLibrary library : p.getComponents().withType(CppLibrary.class)) {
                        result.add(new DefaultLibraryProduct(library.getBaseName().get(), p.getProjectDir(), library.getCppSource(), mapDependencies(library.getImplementationDependencies())));
                    }
                    for (SwiftApplication application : p.getComponents().withType(SwiftApplication.class)) {
                        result.add(new DefaultExecutableProduct(application.getModule().get(), p.getProjectDir(), application.getSwiftSource(), mapDependencies(application.getImplementationDependencies())));
                    }
                    for (SwiftLibrary library : p.getComponents().withType(SwiftLibrary.class)) {
                        result.add(new DefaultLibraryProduct(library.getModule().get(), p.getProjectDir(), library.getSwiftSource(), mapDependencies(library.getImplementationDependencies())));
                    }
                }
                return result;
            }
        });
        manifestTask.getProducts().set(products);
    }

    private List<String> mapDependencies(Configuration configuration) {
        // TODO - should use publication service to do this lookup, deal with ambiguous reference
        DomainObjectSet<ProjectDependency> dependencies = configuration.getAllDependencies().withType(ProjectDependency.class);
        List<String> result = new ArrayList<String>();
        for (ProjectDependency dependency : dependencies) {
            for (SwiftLibrary library : dependency.getDependencyProject().getComponents().withType(SwiftLibrary.class)) {
                result.add(library.getModule().get());
            }
            for (CppLibrary library : dependency.getDependencyProject().getComponents().withType(CppComponent.class).withType(CppLibrary.class)) {
                result.add(library.getBaseName().get());
            }
        }
        return result;
    }
}
