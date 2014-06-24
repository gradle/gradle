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

package org.gradle.runtime.jvm.internal.plugins;

import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.model.ModelRule;
import org.gradle.model.Path;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.runtime.jvm.JvmLibrary;
import org.gradle.runtime.jvm.internal.DefaultJvmLibraryBinary;

import java.io.File;

public class CreateJvmBinaries extends ModelRule {
    private final BinaryNamingSchemeBuilder namingSchemeBuilder;
    private final File binariesDir;
    private final File classesDir;

    // TODO:DAZ Add a ProjectLayout model that can be input to a rule
    public CreateJvmBinaries(BinaryNamingSchemeBuilder namingSchemeBuilder, File buildDir) {
        this.namingSchemeBuilder = namingSchemeBuilder;
        this.binariesDir = new File(buildDir, "binaries");
        this.classesDir = new File(buildDir, "classes");
    }

    void createBinaries(BinaryContainer binaries, @Path("jvm.libraries") NamedDomainObjectCollection<JvmLibrary> libraries) {
        for (JvmLibrary jvmLibrary : libraries) {
            BinaryNamingScheme namingScheme = namingSchemeBuilder
                    .withComponentName(jvmLibrary.getName())
                    .withTypeString("jar")
                    .build();
            DefaultJvmLibraryBinary jvmLibraryBinary = new DefaultJvmLibraryBinary(jvmLibrary, namingScheme);
            jvmLibraryBinary.source(jvmLibrary.getSource());
            configureBinaryOutputLocations(jvmLibraryBinary);
            binaries.add(jvmLibraryBinary);
        }
    }

    private void configureBinaryOutputLocations(DefaultJvmLibraryBinary jvmLibraryBinary) {
        String outputBaseName = jvmLibraryBinary.getNamingScheme().getOutputDirectoryBase();
        jvmLibraryBinary.setClassesDir(new File(classesDir, outputBaseName));
        jvmLibraryBinary.setJarFile(new File(binariesDir, String.format("%s.jar", outputBaseName)));
    }
}
