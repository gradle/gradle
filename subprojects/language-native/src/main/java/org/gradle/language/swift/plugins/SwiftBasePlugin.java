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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.nativeplatform.internal.DefaultNativeComponent;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.language.swift.ProductionSwiftComponent;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPathLocator;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;
import org.gradle.swiftpm.internal.NativeProjectPublication;
import org.gradle.swiftpm.internal.SwiftPmTarget;

import javax.inject.Inject;

/**
 * A common base plugin for the Swift application and library plugins
 *
 * @since 4.1
 */
public abstract class SwiftBasePlugin implements Plugin<Project> {
    private final ProjectPublicationRegistry publicationRegistry;
    private final MacOSSdkPathLocator locator;

    @Inject
    public SwiftBasePlugin(ProjectPublicationRegistry publicationRegistry, MacOSSdkPathLocator locator) {
        this.publicationRegistry = publicationRegistry;
        this.locator = locator;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBasePlugin.class);
        project.getPluginManager().apply(SwiftCompilerPlugin.class);

        final TaskContainer tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

        project.getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE).getCompatibilityRules().add(SwiftCppUsageCompatibilityRule.class);

        project.getComponents().withType(DefaultSwiftBinary.class, binary -> {
            final Names names = binary.getNames();
            TaskProvider<SwiftCompile> compile = tasks.register(names.getCompileTaskName("swift"), SwiftCompile.class, task -> {
                task.getModules().from(binary.getCompileModules());
                task.getSource().from(binary.getSwiftSource());
                task.getDebuggable().set(binary.isDebuggable());
                task.getOptimized().set(binary.isOptimized());
                if (binary.isTestable()) {
                    task.getCompilerArgs().add("-enable-testing");
                }
                if (binary.getTargetMachine().getOperatingSystemFamily().isMacOs()) {
                    task.getCompilerArgs().add("-sdk");
                    task.getCompilerArgs().add(locator.find().getAbsolutePath());
                }
                task.getModuleName().set(binary.getModule());
                task.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));
                task.getModuleFile().set(buildDirectory.file(binary.getModule().map(moduleName -> "modules/" + names.getDirName() + moduleName + ".swiftmodule")));
                task.getSourceCompatibility().set(binary.getTargetPlatform().getSourceCompatibility());

                task.getTargetPlatform().set(binary.getNativePlatform());

                // TODO - make this lazy
                task.getToolChain().set(binary.getToolChain());

                if (binary instanceof SwiftSharedLibrary || binary instanceof SwiftStaticLibrary) {
                    task.getCompilerArgs().add("-parse-as-library");
                }
            });

            binary.getModuleFile().set(compile.flatMap(task -> task.getModuleFile()));
            binary.getCompileTask().set(compile);
            binary.getObjectsDir().set(compile.flatMap(task -> task.getObjectFileDir()));
        });

        project.getComponents().withType(ProductionSwiftComponent.class, component -> {
            project.afterEvaluate(p -> {
                DefaultNativeComponent componentInternal = (DefaultNativeComponent) component;
                publicationRegistry.registerPublication((ProjectInternal) project, new NativeProjectPublication(componentInternal.getDisplayName(), new SwiftPmTarget(component.getModule().get())));
            });
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
}
