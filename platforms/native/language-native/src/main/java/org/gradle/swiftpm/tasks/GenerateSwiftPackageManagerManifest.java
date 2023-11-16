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

package org.gradle.swiftpm.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.nativeplatform.Linkage;
import org.gradle.swiftpm.Package;
import org.gradle.swiftpm.internal.AbstractProduct;
import org.gradle.swiftpm.internal.BranchDependency;
import org.gradle.swiftpm.internal.DefaultLibraryProduct;
import org.gradle.swiftpm.internal.DefaultPackage;
import org.gradle.swiftpm.internal.DefaultTarget;
import org.gradle.swiftpm.internal.Dependency;
import org.gradle.swiftpm.internal.VersionDependency;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * A task that produces a Swift Package Manager manifest.
 *
 * @since 4.6
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class GenerateSwiftPackageManagerManifest extends DefaultTask {
    private final RegularFileProperty manifestFile;
    private final Property<Package> packageProperty;

    public GenerateSwiftPackageManagerManifest() {
        ObjectFactory objectFactory = getProject().getObjects();
        manifestFile = objectFactory.fileProperty();
        packageProperty = objectFactory.property(Package.class);
    }

    @Input
    public Property<Package> getPackage() {
        return packageProperty;
    }

    @OutputFile
    public RegularFileProperty getManifestFile() {
        return manifestFile;
    }

    @TaskAction
    public void generate() {
        DefaultPackage srcPackage = (DefaultPackage) packageProperty.get();
        Path manifest = manifestFile.get().getAsFile().toPath();
        try {
            Path baseDir = manifest.getParent();
            Files.createDirectories(baseDir);
            PrintWriter writer = new PrintWriter(Files.newBufferedWriter(manifest, Charset.forName("utf-8")));
            try {
                writer.println("// swift-tools-version:4.0");
                writer.println("//");
                writer.println("// GENERATED FILE - do not edit");
                writer.println("//");
                writer.println("import PackageDescription");
                writer.println();
                writer.println("let package = Package(");
                writer.println("    name: \"" + getProject().getName() + "\",");
                writer.println("    products: [");
                for (AbstractProduct product : srcPackage.getProducts()) {
                    if (product.isExecutable()) {
                        writer.print("        .executable(");
                        writer.print("name: \"");
                        writer.print(product.getName());
                        writer.print("\"");
                    } else {
                        writer.print("        .library(");
                        writer.print("name: \"");
                        writer.print(product.getName());
                        DefaultLibraryProduct library = (DefaultLibraryProduct) product;
                        if (library.getLinkage() == Linkage.SHARED) {
                            writer.print("\", type: .dynamic");
                        } else {
                            writer.print("\", type: .static");
                        }
                    }
                    writer.print(", targets: [\"");
                    writer.print(product.getTarget().getName());
                    writer.println("\"]),");
                }
                writer.println("    ],");
                if (!srcPackage.getDependencies().isEmpty()) {
                    writer.println("    dependencies: [");
                    for (Dependency dependency : srcPackage.getDependencies()) {
                        writer.print("        .package(url: \"");
                        if (dependency.getUrl().getScheme().equals("file")) {
                            writer.print(baseDir.relativize(new File(dependency.getUrl()).toPath()));
                        } else {
                            writer.print(dependency.getUrl());
                        }
                        writer.print("\", ");
                        if (dependency instanceof VersionDependency) {
                            VersionDependency versionDependency = (VersionDependency) dependency;
                            if (versionDependency.getUpperBound() == null) {
                                writer.print("from: \"");
                                writer.print(versionDependency.getLowerBound());
                                writer.print("\"");
                            } else if (versionDependency.isUpperInclusive()){
                                writer.print("\"");
                                writer.print(versionDependency.getLowerBound());
                                writer.print("\"...\"");
                                writer.print(versionDependency.getUpperBound());
                                writer.print("\"");
                            }  else {
                                writer.print("\"");
                                writer.print(versionDependency.getLowerBound());
                                writer.print("\"..<\"");
                                writer.print(versionDependency.getUpperBound());
                                writer.print("\"");
                            }
                        } else {
                            writer.print(".branch(\"");
                            writer.print(((BranchDependency) dependency).getBranch());
                            writer.print("\")");
                        }
                        writer.println("),");
                    }
                    writer.println("    ],");
                }
                writer.println("    targets: [");
                for (DefaultTarget target : srcPackage.getTargets()) {
                    writer.println("        .target(");
                    writer.print("            name: \"");
                    writer.print(target.getName());
                    writer.println("\",");
                    if (!target.getRequiredTargets().isEmpty() || !target.getRequiredProducts().isEmpty()) {
                        writer.println("            dependencies: [");
                        for (String dep : target.getRequiredTargets()) {
                            writer.print("                .target(name: \"");
                            writer.print(dep);
                            writer.println("\"),");
                        }
                        for (String dep : target.getRequiredProducts()) {
                            writer.print("                .product(name: \"");
                            writer.print(dep);
                            writer.println("\"),");
                        }
                        writer.println("            ],");
                    }
                    writer.print("            path: \"");
                    Path productPath = target.getPath().toPath();
                    String relPath = baseDir.relativize(productPath).toString();
                    writer.print(relPath.isEmpty() ? "." : relPath);
                    writer.println("\",");
                    writer.println("            sources: [");
                    Set<String> sorted = new TreeSet<String>();
                    for (File sourceFile : target.getSourceFiles()) {
                        sorted.add(productPath.relativize(sourceFile.toPath()).toString());
                    }
                    for (String sourcePath : sorted) {
                        writer.print("                \"");
                        writer.print(sourcePath);
                        writer.println("\",");
                    }
                    writer.print("            ]");
                    if (target.getPublicHeaderDir() != null) {
                        writer.println(",");
                        writer.print("            publicHeadersPath: \"");
                        writer.print(productPath.relativize(target.getPublicHeaderDir().toPath()));
                        writer.print("\"");
                    }
                    writer.println();
                    writer.println("        ),");
                }
                writer.print("    ]");
                if (srcPackage.getSwiftLanguageVersion() != null) {
                    writer.println(",");
                    writer.print("    swiftLanguageVersions: [");
                    writer.print(srcPackage.getSwiftLanguageVersion().getVersion());
                    writer.print("]");
                }
                writer.println();
                writer.println(")");
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new GradleException(String.format("Could not write manifest file %s.", manifest), e);
        }
    }
}
