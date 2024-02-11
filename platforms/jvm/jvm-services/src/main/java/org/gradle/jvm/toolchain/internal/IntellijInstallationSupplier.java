/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.os.OperatingSystem;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;

public class IntellijInstallationSupplier extends AutoDetectingInstallationSupplier {

    private static final String IDEA_JDKS_DIRECTORY_PROPERTY = "org.gradle.java.installations.idea-jdks-directory";

    private final Provider<String> ideaJdksDirectory;
    private final FileResolver fileResolver;

    @Inject
    public IntellijInstallationSupplier(ProviderFactory factory, FileResolver fileResolver) {
        this(factory, fileResolver, OperatingSystem.current());
    }

    public IntellijInstallationSupplier(ProviderFactory factory, FileResolver fileResolver, OperatingSystem os) {
        super(factory);
        this.ideaJdksDirectory = factory.gradleProperty(IDEA_JDKS_DIRECTORY_PROPERTY).orElse(defaultJdksDirectory(os));
        this.fileResolver = fileResolver;
    }

    private String defaultJdksDirectory(OperatingSystem os) {
        if (os.isMacOsX()) {
            return new File(System.getProperty("user.home"), "Library/Java/JavaVirtualMachines").getAbsolutePath();
        }
        return new File(System.getProperty("user.home"), ".jdks").getAbsolutePath();
    }

    @Override
    public String getSourceName() {
        return "IntelliJ IDEA";
    }

    @Override
    protected Set<InstallationLocation> findCandidates() {
        File directory = fileResolver.resolve(ideaJdksDirectory.get());
        return FileBasedInstallationFactory.fromDirectory(directory, getSourceName());
    }
}
