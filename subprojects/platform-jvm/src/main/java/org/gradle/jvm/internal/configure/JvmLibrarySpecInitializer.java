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
import org.gradle.api.InvalidUserDataException;
import org.gradle.jvm.JvmLibrarySpec;
import org.gradle.jvm.platform.JvmPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;

import java.util.ArrayList;
import java.util.List;

public class JvmLibrarySpecInitializer implements Action<JvmLibrarySpec> {
    private final JarBinariesFactory factory;
    private final BinaryNamingSchemeBuilder namingSchemeBuilder;
    private final JavaToolChain toolChain;
    private final PlatformContainer platforms;

    public JvmLibrarySpecInitializer(JarBinariesFactory factory, BinaryNamingSchemeBuilder namingSchemeBuilder, JavaToolChain toolChain, PlatformContainer platforms) {
        this.factory = factory;
        this.namingSchemeBuilder = namingSchemeBuilder;
        this.toolChain = toolChain;
        this.platforms = platforms;
    }

    public void execute(JvmLibrarySpec jvmLibrary) {
        List<JvmPlatform> selectedPlatforms = platforms.select(JvmPlatform.class, jvmLibrary.getTargetPlatforms());
        if (selectedPlatforms.size() != jvmLibrary.getTargetPlatforms().size()) { //TODO: factor out
            List<String> notFound = new ArrayList<String>();
            for (String target: jvmLibrary.getTargetPlatforms()) {
                Platform found = platforms.findByName(target);
                if (found == null || !(found instanceof JvmPlatform)) {
                    notFound.add(target);
                }
            }

            //TODO freekh: create test case for this:
            //TODO freekh: use some other exceptions? Check this somewhere else?
            if (notFound.size() == 1) {
                throw new InvalidUserDataException("Could not find JvmPlatform with name: '" + notFound.get(0) + "'");
            } else if (notFound.size() > 1) {
                throw new InvalidUserDataException("Could not find JvmPlatforms with names: " + notFound);
            } else {
                throw new InvalidUserDataException("Could not determine JvmPlatforms: " + jvmLibrary.getTargetPlatforms());
            }
        }
        for (JvmPlatform platform: selectedPlatforms) { //TODO freekh: check empty/use default
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
