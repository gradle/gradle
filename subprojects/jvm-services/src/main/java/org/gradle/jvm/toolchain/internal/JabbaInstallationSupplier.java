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

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Set;

public class JabbaInstallationSupplier extends AutoDetectingInstallationSupplier {

    private Provider<String> jabbaHomeDir;

    @Inject
    public JabbaInstallationSupplier(ProviderFactory factory) {
        super(factory);
        jabbaHomeDir = getEnvironmentProperty("JABBA_HOME");
    }

    @Override
    public String getSourceName() {
        return "Jabba";
    }

    @Override
    protected Set<InstallationLocation> findCandidates() {
        return jabbaHomeDir.map(findJavaCandidates()).getOrElse(Collections.emptySet());
    }

    private Transformer<Set<InstallationLocation>, String> findJavaCandidates() {
        return jabbaHome -> {
            final File root = new File(jabbaHome, "jdk");
            return FileBasedInstallationFactory.fromDirectory(root, getSourceName());
        };
    }

}
