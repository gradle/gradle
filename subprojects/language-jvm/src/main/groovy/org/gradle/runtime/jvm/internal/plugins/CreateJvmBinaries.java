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

import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.LibraryContainer;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.runtime.jvm.JvmLibrary;
import org.gradle.runtime.jvm.internal.DefaultJvmLibraryBinary;
import org.gradle.model.ModelRule;

public class CreateJvmBinaries extends ModelRule {
    private final BinaryNamingSchemeBuilder namingSchemeBuilder;

    public CreateJvmBinaries(BinaryNamingSchemeBuilder namingSchemeBuilder) {
        this.namingSchemeBuilder = namingSchemeBuilder;
    }

    void createBinaries(BinaryContainer binaries, LibraryContainer libraries) {
        for (JvmLibrary jvmLibrary : libraries.withType(JvmLibrary.class)) {
            BinaryNamingScheme namingScheme = namingSchemeBuilder
                    .withComponentName(jvmLibrary.getName())
                    .withTypeString("jar")
                    .build();
            binaries.add(new DefaultJvmLibraryBinary(jvmLibrary, namingScheme));
        }
    }
}
