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

package org.gradle.nativecode.base.internal;

import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativecode.base.Library;
import org.gradle.nativecode.base.StaticLibraryBinary;

public class DefaultStaticLibraryBinary extends DefaultNativeBinary implements StaticLibraryBinary {
    private final Library library;

    public DefaultStaticLibraryBinary(Library library) {
        this.library = library;
    }

    public Library getComponent() {
        return library;
    }

    public String getName() {
        return library.getName() + "StaticLibrary";
    }

    @Override
    public String toString() {
        return String.format("static library '%s'", library.getName());
    }

    public String getOutputFileName() {
        return OperatingSystem.current().getStaticLibraryName(getComponent().getBaseName());
    }
}
