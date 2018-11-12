/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.language.swift.ProductionSwiftComponent;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.internal.DefaultSwiftComponent;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPathLocator;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;
import org.gradle.swiftpm.internal.NativeProjectPublication;
import org.gradle.swiftpm.internal.SwiftPmTarget;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * A common base plugin for the Swift application and library plugins
 *
 * @since 4.1
 */
@Incubating
public class SwiftBasePlugin implements Plugin<ProjectInternal> {
    private final ProjectPublicationRegistry publicationRegistry;
    private final MacOSSdkPathLocator locator;

    @Inject
    public SwiftBasePlugin(ProjectPublicationRegistry publicationRegistry, MacOSSdkPathLocator locator) {
        this.publicationRegistry = publicationRegistry;
        this.locator = locator;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(NativeBasePlugin.class);
        project.getPluginManager().apply(SwiftCompilerPlugin.class);

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
        final ProviderFactory providers = project.getProviders();

        project.getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE).getCompatibilityRules().add(SwiftCppUsageCompatibilityRule.class);

        project.getComponents().withType(DefaultSwiftBinary.class, new Action<DefaultSwiftBinary>() {
            @Override
            public void execute(final DefaultSwiftBinary binary) {
                final Names names = binary.getNames();
                TaskProvider<SwiftCompile> compile = tasks.register(names.getCompileTaskName("swift"), SwiftCompile.class, new Action<SwiftCompile>() {
                    @Override
                    public void execute(SwiftCompile compile) {
                        compile.getModules().from(binary.getCompileModules());
                        compile.getSource().from(binary.getSwiftSource());
                        compile.getDebuggable().set(binary.isDebuggable());
                        compile.getOptimized().set(binary.isOptimized());
                        if (binary.isTestable()) {
                            compile.getCompilerArgs().add("-enable-testing");
                        }
                        if (binary.getTargetPlatform().getOperatingSystemFamily().isMacOs()) {
                            compile.getCompilerArgs().add("-sdk");
                            compile.getCompilerArgs().add(locator.find().getAbsolutePath());
                        }
                        compile.getModuleName().set(binary.getModule());
                        compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));
                        compile.getModuleFile().set(buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return "modules/" + names.getDirName() + binary.getModule().get() + ".swiftmodule";
                            }
                        })));
                        compile.getSourceCompatibility().set(binary.getSourceCompatibility());

                        compile.getTargetPlatform().set(binary.getTargetPlatform());

                        // TODO - make this lazy
                        compile.getToolChain().set(binary.getToolChain());

                        if (binary instanceof SwiftSharedLibrary || binary instanceof SwiftStaticLibrary) {
                            compile.getCompilerArgs().add("-parse-as-library");
                        }
                    }
                });

                binary.getModuleFile().set(compile.flatMap(new Transformer<Provider<? extends RegularFile>, SwiftCompile>() {
                    @Override
                    public Provider<? extends RegularFile> transform(SwiftCompile swiftCompile) {
                        return swiftCompile.getModuleFile();
                    }
                }));
                binary.getCompileTask().set(compile);
                binary.getObjectsDir().set(compile.flatMap(new Transformer<Provider<? extends Directory>, SwiftCompile>() {
                    @Override
                    public Provider<? extends Directory> transform(SwiftCompile swiftCompile) {
                        return swiftCompile.getObjectFileDir();
                    }
                }));
            }
        });

        project.getComponents().withType(DefaultSwiftComponent.class, new Action<DefaultSwiftComponent>() {
            @Override
            public void execute(final DefaultSwiftComponent component) {
                component.getBinaries().whenElementKnown(DefaultSwiftBinary.class, new Action<DefaultSwiftBinary>() {
                    @Override
                    public void execute(final DefaultSwiftBinary binary) {
                        Provider<SwiftVersion> swiftLanguageVersionProvider = project.provider(new Callable<SwiftVersion>() {
                            @Override
                            public SwiftVersion call() throws Exception {
                                component.getSourceCompatibility().finalizeValue();
                                SwiftVersion swiftSourceCompatibility = component.getSourceCompatibility().getOrNull();
                                if (swiftSourceCompatibility == null) {
                                    return toSwiftVersion(binary.getPlatformToolProvider().getCompilerMetadata(ToolType.SWIFT_COMPILER).getVersion());
                                }
                                return swiftSourceCompatibility;
                            }
                        });

                        binary.getSourceCompatibility().set(swiftLanguageVersionProvider);
                    }
                });
            }
        });
        project.getComponents().withType(ProductionSwiftComponent.class, new Action<ProductionSwiftComponent>() {
            @Override
            public void execute(final ProductionSwiftComponent component) {
                project.afterEvaluate(new Action<Project>() {
                    @Override
                    public void execute(Project project) {
                        DefaultSwiftComponent componentInternal = (DefaultSwiftComponent) component;
                        ProjectInternal projectInternal = (ProjectInternal) project;
                        publicationRegistry.registerPublication(projectInternal, new NativeProjectPublication(componentInternal.getDisplayName(), new SwiftPmTarget(component.getModule().get())));
                    }
                });
            }
        });
    }

    static class SwiftCppUsageCompatibilityRule implements AttributeCompatibilityRule<Usage> {
        @Override
        public void execute(CompatibilityCheckDetails<Usage> details) {
            if (Usage.SWIFT_API.equals(details.getConsumerValue().getName())
                && Usage.C_PLUS_PLUS_API.equals(details.getProducerValue().getName())) {
                details.compatible();
            }
        }
    }

    static SwiftVersion toSwiftVersion(VersionNumber swiftCompilerVersion) {
        if (swiftCompilerVersion.getMajor() == 3) {
            return SwiftVersion.SWIFT3;
        } else if (swiftCompilerVersion.getMajor() == 4) {
            return SwiftVersion.SWIFT4;
        } else {
            throw new IllegalArgumentException(String.format("Swift language version is unknown for the specified Swift compiler version (%s)", swiftCompilerVersion.toString()));
        }
    }
}
