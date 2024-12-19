/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.deprecation.Documentation;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainRequest;
import org.gradle.jvm.toolchain.JavaToolchainResolver;
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainProvisioningNotConfiguredException;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

public class DefaultJavaToolchainResolverService implements JavaToolchainResolverService {

    private final JavaToolchainResolverRegistryInternal toolchainResolverRegistry;

    @Inject
    public DefaultJavaToolchainResolverService(JavaToolchainResolverRegistryInternal toolchainResolverRegistry) {
        this.toolchainResolverRegistry = toolchainResolverRegistry;
    }

    @Override
    public Optional<JavaToolchainDownload> tryResolve(JavaToolchainRequest request) {
        List<? extends RealizedJavaToolchainRepository> repositories = toolchainResolverRegistry.requestedRepositories();
        if (repositories.isEmpty()) {
            throw new ToolchainProvisioningNotConfiguredException(request.getJavaToolchainSpec(), "Toolchain download repositories have not been configured.",
                "Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".",
                "Learn more about toolchain repositories at " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl() + ".");
        }
        for (RealizedJavaToolchainRepository repository : repositories) {
            JavaToolchainResolver resolver = repository.getResolver();
            Optional<JavaToolchainDownload> download = resolver.resolve(request);

            if (download.isPresent()) {
                return download;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean hasConfiguredToolchainRepositories() {
        return !toolchainResolverRegistry.requestedRepositories().isEmpty();
    }
}
