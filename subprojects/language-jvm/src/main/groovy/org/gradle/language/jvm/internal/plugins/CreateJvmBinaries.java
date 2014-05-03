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

package org.gradle.language.jvm.internal.plugins;

import org.gradle.language.base.BinaryContainer;
import org.gradle.language.base.LibraryContainer;
import org.gradle.language.jvm.JvmLibrary;
import org.gradle.language.jvm.JvmLibraryBinary;
import org.gradle.model.ModelRule;

public class CreateJvmBinaries extends ModelRule {
    void createBinaries(BinaryContainer binaries, LibraryContainer libraries) {
        for (JvmLibrary jvmLibrary : libraries.withType(JvmLibrary.class)) {
            // TODO:DAZ Better name
            String binaryName = jvmLibrary.getName() + "Binary";
            binaries.create(binaryName, JvmLibraryBinary.class);
        }
    }
}
