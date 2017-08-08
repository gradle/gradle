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
     * The name of the api configuration.
     */
    public static final String API = "api";

    /**
     * The name of the Swift compile import path configuration.
     */
    public static final String SWIFT_IMPORT_PATH = "swiftImportPath";

    /**
     * The name of the test implementation configuration.
     *
     * @since 4.2
     */
    public static final String TEST_IMPLEMENTATION = "testImplementation";

    /**
     * The name of the Swift test compile import path configuration.
     *
     * @since 4.2
     */
    public static final String SWIFT_TEST_IMPORT_PATH = "swiftTestImportPath";

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(SwiftCompilerPlugin.class);

        // TODO - Merge with CppBasePlugin to remove code duplication
        Configuration api = project.getConfigurations().create(API);
        api.setCanBeConsumed(false);
        api.setCanBeResolved(false);

        Configuration implementation = project.getConfigurations().create(IMPLEMENTATION);
        implementation.extendsFrom(api);
        implementation.setCanBeConsumed(false);
        implementation.setCanBeResolved(false);

        Configuration importPath = project.getConfigurations().create(SWIFT_IMPORT_PATH);
        importPath.extendsFrom(implementation);
        importPath.setCanBeConsumed(false);
        importPath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.SWIFT_API));

        Configuration nativeLink = project.getConfigurations().create(CppBasePlugin.NATIVE_LINK);
        nativeLink.extendsFrom(implementation);
        nativeLink.setCanBeConsumed(false);
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_LINK));

        Configuration nativeRuntime = project.getConfigurations().create(CppBasePlugin.NATIVE_RUNTIME);
        nativeRuntime.extendsFrom(implementation);
        nativeRuntime.setCanBeConsumed(false);
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_RUNTIME));

        Configuration testImplementation = project.getConfigurations().create(TEST_IMPLEMENTATION);
        testImplementation.extendsFrom(implementation);
        testImplementation.setCanBeConsumed(false);
        testImplementation.setCanBeResolved(false);

        Configuration testImportPath = project.getConfigurations().create(SWIFT_TEST_IMPORT_PATH);
        testImportPath.extendsFrom(implementation);
        testImportPath.setCanBeConsumed(false);
        testImportPath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.SWIFT_API));

        Configuration testNativeLink = project.getConfigurations().create(CppBasePlugin.NATIVE_TEST_LINK);
        testNativeLink.extendsFrom(testImplementation);
        testNativeLink.setCanBeConsumed(false);
        testNativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_LINK));

        Configuration testNativeRuntime = project.getConfigurations().create(CppBasePlugin.NATIVE_TEST_RUNTIME);
        testNativeRuntime.extendsFrom(testImplementation);
        testNativeRuntime.setCanBeConsumed(false);
        testNativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_RUNTIME));
    }
}
