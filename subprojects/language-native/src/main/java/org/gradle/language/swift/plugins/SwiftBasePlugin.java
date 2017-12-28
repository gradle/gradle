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
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;

import java.util.concurrent.Callable;

/**
 * A common base plugin for the Swift application and library plugins
 *
 * @since 4.1
 */
@Incubating
public class SwiftBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(ProjectInternal project) {
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
                SwiftCompile compile = tasks.create(names.getCompileTaskName("swift"), SwiftCompile.class);
                compile.getModules().from(binary.getCompileModules());
                compile.getSource().from(binary.getSwiftSource());
                if (binary.isDebuggable()) {
                    compile.setDebuggable(true);
                }
                if (binary.isOptimized()) {
                    compile.setOptimized(true);
                }
                if (binary.isTestable()) {
                    compile.getCompilerArgs().add("-enable-testing");
                }
                compile.getModuleName().set(binary.getModule());
                compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));
                compile.getModuleFile().set(buildDirectory.file(providers.provider(new Callable<String>() {
                    @Override
                    public String call() {
                        return "modules/" + names.getDirName() + binary.getModule().get() + ".swiftmodule";
                    }
                })));
                binary.getModuleFile().set(compile.getModuleFile());

                NativePlatform currentPlatform = binary.getTargetPlatform();
                compile.setTargetPlatform(currentPlatform);

                // TODO - make this lazy
                NativeToolChainInternal toolChain = binary.getToolChain();
                compile.setToolChain(toolChain);

                binary.getCompileTask().set(compile);
                binary.getObjectsDir().set(compile.getObjectFileDir());
            }
        });
        project.getComponents().withType(SwiftSharedLibrary.class, new Action<SwiftSharedLibrary>() {
            @Override
            public void execute(SwiftSharedLibrary library) {
                // Specific compiler arguments
                library.getCompileTask().get().getCompilerArgs().add("-parse-as-library");
            }
        });
        project.getComponents().withType(SwiftStaticLibrary.class, new Action<SwiftStaticLibrary>() {
            @Override
            public void execute(SwiftStaticLibrary library) {
                // Specific compiler arguments
                library.getCompileTask().get().getCompilerArgs().add("-parse-as-library");
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
}
