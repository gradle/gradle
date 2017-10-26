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

package org.gradle.nativeplatform.toolchain.internal.gcc.version;

import java.io.File;
import java.util.List;

public interface CompilerMetaDataProvider {

    enum CompilerType {
        GCC("gcc", "GCC"),
        CLANG("clang", "Clang");

        private final String identifier;
        private final String description;

        CompilerType(String identifier, String description) {
            this.identifier = identifier;
            this.description = description;
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getDescription() {
            return description;
        }
    }

    GccVersionResult getGccMetaData(File gccBinary, List<String> additionalArgs);

    CompilerType getCompilerType();

}
