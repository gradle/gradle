/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainUsageProgressDetails.JavaTool;

import javax.inject.Inject;

public class DefaultJavaToolchainService implements JavaToolchainService {

    private final JavaToolchainQueryService queryService;
    private final ObjectFactory objectFactory;
    private final JavaCompilerFactory compilerFactory;
    private final ToolchainToolFactory toolFactory;
    private final BuildOperationProgressEventEmitter eventEmitter;

    @Inject
    public DefaultJavaToolchainService(
        JavaToolchainQueryService queryService,
        ObjectFactory objectFactory,
        JavaCompilerFactory compilerFactory,
        ToolchainToolFactory toolFactory,
        BuildOperationProgressEventEmitter eventEmitter
    ) {
        this.queryService = queryService;
        this.objectFactory = objectFactory;
        this.compilerFactory = compilerFactory;
        this.toolFactory = toolFactory;
        this.eventEmitter = eventEmitter;
    }

    @Override
    public Provider<JavaCompiler> compilerFor(Action<? super JavaToolchainSpec> config) {
        return compilerFor(configureToolchainSpec(config));
    }

    @Override
    public Provider<JavaCompiler> compilerFor(JavaToolchainSpec spec) {
        return queryService.findMatchingToolchain(spec)
            .withSideEffect(toolchain -> emitEvent(toolchain, JavaTool.COMPILER))
            .map(javaToolchain -> new DefaultToolchainJavaCompiler(javaToolchain, compilerFactory));
    }

    @Override
    public Provider<JavaLauncher> launcherFor(Action<? super JavaToolchainSpec> config) {
        return launcherFor(configureToolchainSpec(config));
    }

    @Override
    public Provider<JavaLauncher> launcherFor(JavaToolchainSpec spec) {
        return queryService.findMatchingToolchain(spec)
            .withSideEffect(toolchain -> emitEvent(toolchain, JavaTool.LAUNCHER))
            .map(DefaultToolchainJavaLauncher::new);
    }

    @Override
    public Provider<JavadocTool> javadocToolFor(Action<? super JavaToolchainSpec> config) {
        return javadocToolFor(configureToolchainSpec(config));
    }

    @Override
    public Provider<JavadocTool> javadocToolFor(JavaToolchainSpec spec) {
        return queryService.findMatchingToolchain(spec)
            .withSideEffect(toolchain -> emitEvent(toolchain, JavaTool.JAVADOC))
            .map(javaToolchain -> toolFactory.create(JavadocTool.class, javaToolchain));
    }

    private DefaultToolchainSpec configureToolchainSpec(Action<? super JavaToolchainSpec> config) {
        DefaultToolchainSpec toolchainSpec = objectFactory.newInstance(DefaultToolchainSpec.class);
        config.execute(toolchainSpec);
        return toolchainSpec;
    }

    private void emitEvent(JavaToolchain toolchain, JavaTool toolName) {
        eventEmitter.emitNowForCurrent(new DefaultJavaToolchainUsageProgressDetails(toolName, toolchain.getMetadata()));
    }

}
