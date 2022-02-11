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

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.os.OperatingSystem;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;

public class IntellijIdeaInstallationSupplier extends AutoDetectingInstallationSupplier {

    private static final String PROPERTY_NAME = "org.gradle.java.installations.idea-jdks-directory";

    private final Provider<String> root;

    @Inject
    public IntellijIdeaInstallationSupplier(ProviderFactory factory) {
        this(factory, OperatingSystem.current());
    }

    public IntellijIdeaInstallationSupplier(ProviderFactory factory, OperatingSystem os) {
        super(factory);
        root = factory.gradleProperty(PROPERTY_NAME).orElse(defaultJdksDirectory(os));
    }

    private String defaultJdksDirectory(OperatingSystem os) {
        if (os.isMacOsX()) {
            return new File(System.getProperty("user.home"), "Library/Java/JavaVirtualMachines").getAbsolutePath();
        }
        return new File(System.getProperty("user.home"), ".jdks").getAbsolutePath();
    }

    @Override
    protected Set<InstallationLocation> findCandidates() {
        File rootDirectory = new File(root.get());
        return FileBasedInstallationFactory.fromDirectory(rootDirectory, "IntelliJ IDEA");
    }
}
