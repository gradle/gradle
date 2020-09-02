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
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JavadocTool;

import javax.inject.Inject;

public class DefaultJavaToolchainService implements JavaToolchainService {

    private final JavaToolchainQueryService queryService;

    @Inject
    public DefaultJavaToolchainService(JavaToolchainQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public Provider<JavaCompiler> compilerFrom(Action<? super JavaToolchainSpec> config) {
        return queryService.compilerFrom(config);
    }

    @Override
    public Provider<JavaLauncher> launcherFrom(Action<? super JavaToolchainSpec> config) {
        return queryService.launcherFrom(config);
    }

    @Override
    public Provider<JavadocTool> javadocToolFrom(Action<? super JavaToolchainSpec> config) {
        return queryService.javadocToolFrom(config);
    }
}
