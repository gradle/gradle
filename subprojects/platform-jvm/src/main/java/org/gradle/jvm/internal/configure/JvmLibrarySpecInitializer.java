/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm.internal.configure;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;

import java.util.Collections;
import java.util.List;

public class JvmLibrarySpecInitializer implements Action<JvmLibrarySpec> {
    private final JarBinariesFactory factory;
    private final BinaryNamingSchemeBuilder namingSchemeBuilder;
    private final JavaToolChainRegistry toolChains;
    private final PlatformContainer platforms;

    public JvmLibrarySpecInitializer(JarBinariesFactory factory, BinaryNamingSchemeBuilder namingSchemeBuilder, JavaToolChainRegistry toolChains, PlatformContainer platforms) {
        this.factory = factory;
        this.namingSchemeBuilder = namingSchemeBuilder;
        this.toolChains = toolChains;
        this.platforms = platforms;
    }

    public void execute(JvmLibrarySpec jvmLibrary) {
        List<String> targetPlatforms = jvmLibrary.getTargetPlatforms();
        // TODO:DAZ We should have a generic (JVM + Native) way to get the 'best' platform to build when no target is defined.
        // This logic needs to inspect the available platforms and find the closest one matching the current platform
        if (targetPlatforms.isEmpty()) {
            targetPlatforms = Collections.singletonList(new DefaultJavaPlatform(JavaVersion.current()).getName());
        }
        List<JavaPlatform> selectedPlatforms = platforms.select(JavaPlatform.class, targetPlatforms);
        for (JavaPlatform platform: selectedPlatforms) {
            JavaToolChain toolChain = toolChains.getForPlatform(platform);
            BinaryNamingSchemeBuilder componentBuilder = namingSchemeBuilder
                    .withComponentName(jvmLibrary.getName())
                    .withTypeString("jar");
            if (selectedPlatforms.size() > 1) { //Only add variant dimension for multiple jdk targets to avoid breaking the default naming scheme
                componentBuilder = componentBuilder.withVariantDimension(platform.getName());
            }
            BinaryNamingScheme namingScheme = componentBuilder.build(); //TODO freekh: move out?
            factory.createJarBinaries(jvmLibrary, namingScheme, toolChain, platform); //TODO: createJarBinaries is mutable! We should not be doing this - execute could return a list instead
        }
    }
}
