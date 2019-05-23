/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.binaryinfo;

import com.google.common.base.MoreObjects;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;

import java.util.List;

public interface BinaryInfo {
    ArchitectureInternal getArch();
    List<String> listObjectFiles();
    List<String> listLinkedLibraries();
    List<Symbol> listSymbols();
    List<Symbol> listDebugSymbols();
    String getSoName();

    class Symbol {
        private final char type;
        private final String name;
        private final boolean exported;

        public Symbol(String name, char type, boolean exported) {
            this.type = type;
            this.name = name;
            this.exported = exported;
        }

        public String getName() {
            return name;
        }

        public char getType() {
            return type;
        }

        public boolean isExported() {
            return exported;
        }

        public String toString() {
            return MoreObjects.toStringHelper(this).add("name", name).add("type", type).add("exported", exported).toString();
        }
    }
}
