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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;

/**
 * A common base plugin for the Swift executable and library plugins
 *
 * @since 4.1
 */
@Incubating
public class SwiftBasePlugin implements Plugin<ProjectInternal> {
    /**
     * The name of the implementation configuration.
     */
    public static final String IMPLEMENTATION = "implementation";

    /**
     * The name of the Swift compile classpath configuration.
     */
    public static final String SWIFT_IMPORT_PATH = "swiftImportPath";

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(SwiftCompilerPlugin.class);

        // TODO - make not consumable or resolvable
        Configuration implementation = project.getConfigurations().create(IMPLEMENTATION);
        implementation.setCanBeConsumed(false);
        implementation.setCanBeResolved(false);

        Configuration includePath = project.getConfigurations().create(SWIFT_IMPORT_PATH);
        includePath.extendsFrom(implementation);
        includePath.setCanBeConsumed(false);
        includePath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.SWIFT_API));

        Configuration nativeLink = project.getConfigurations().create(CppBasePlugin.NATIVE_LINK);
        nativeLink.extendsFrom(implementation);
        nativeLink.setCanBeConsumed(false);
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_LINK));

        Configuration nativeRuntime = project.getConfigurations().create(CppBasePlugin.NATIVE_RUNTIME);
        nativeRuntime.extendsFrom(implementation);
        nativeRuntime.setCanBeConsumed(false);
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_RUNTIME));
    }
}
