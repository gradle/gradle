/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.internal;

import org.gradle.api.Action;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.internal.ProjectLayout;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.ComponentSpec;

import java.io.File;

@SuppressWarnings("UnusedDeclaration")
public class JarBinaryRules extends RuleSource {
    @Defaults
    void configureJarBinaries(final ComponentSpec jvmLibrary, final ProjectLayout projectLayout, final JavaToolChainRegistry toolChains) {
        jvmLibrary.getBinaries().withType(JarBinarySpecInternal.class).beforeEach(new Action<JarBinarySpecInternal>() {
            @Override
            public void execute(JarBinarySpecInternal jarBinary) {
                String libraryName = jarBinary.getId().getLibraryName();

                jarBinary.setClassesDir(jarBinary.getNamingScheme().getOutputDirectory(projectLayout.getBuildDir(), "classes"));
                jarBinary.setResourcesDir(jarBinary.getNamingScheme().getOutputDirectory(projectLayout.getBuildDir(), "resources"));
                File jarsDir = jarBinary.getNamingScheme().getOutputDirectory(projectLayout.getBuildDir(), "jars");
                jarBinary.setJarFile(new File(jarsDir, String.format("%s.jar", libraryName)));
                jarBinary.setApiJarFile(new File(jarsDir, String.format("api/%s.jar", libraryName)));
                jarBinary.setToolChain(toolChains.getForPlatform(jarBinary.getTargetPlatform()));
            }
        });
    }
}
