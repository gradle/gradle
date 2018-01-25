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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.language.ProductionComponent;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.swiftpm.Product;
import org.gradle.swiftpm.internal.DefaultExecutableProduct;
import org.gradle.swiftpm.internal.DefaultLibraryProduct;
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest;

import java.util.LinkedHashSet;
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
                    for (ProductionComponent component : p.getComponents().withType(ProductionComponent.class)) {
                        if (component instanceof CppApplication) {
                            CppApplication application = (CppApplication) component;
                            result.add(new DefaultExecutableProduct(application.getBaseName().get()));
                        } else if (component instanceof CppLibrary) {
                            CppLibrary library = (CppLibrary) component;
                            result.add(new DefaultLibraryProduct(library.getBaseName().get()));
                        } else if (component instanceof SwiftApplication) {
                            SwiftApplication application = (SwiftApplication) component;
                            result.add(new DefaultExecutableProduct(application.getModule().get()));
                        } else if (component instanceof SwiftLibrary) {
                            SwiftLibrary library = (SwiftLibrary) component;
                            result.add(new DefaultLibraryProduct(library.getModule().get()));
                        }
                    }
                }
                return result;
            }
        });
        manifestTask.getProducts().set(products);
    }
}
