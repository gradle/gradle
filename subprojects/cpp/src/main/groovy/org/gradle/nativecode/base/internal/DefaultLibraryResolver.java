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

import org.gradle.api.InvalidUserDataException;
import org.gradle.nativecode.base.*;

class DefaultLibraryResolver implements ConfigurableLibraryResolver {
    private Flavor flavor = Flavor.DEFAULT;
    private Class<? extends LibraryBinary> type = SharedLibraryBinary.class;
    private Library library;

    public DefaultLibraryResolver(Library library) {
        this.library = library;
    }

    public ConfigurableLibraryResolver withFlavor(Flavor flavor) {
        this.flavor = flavor;
        return this;
    }

    public ConfigurableLibraryResolver withType(Class<? extends LibraryBinary> type) {
        this.type = type;
        return this;
    }

    public NativeDependencySet resolve() {
        for (LibraryBinary candidate : library.getBinaries().withType(type)) {
            // If the library has only 1 flavor, then flavor is not important
            if (library.getFlavors().size() == 1) {
                return candidate.resolve();
            }
            // Otherwise match on the flavor
            if (flavor.equals(candidate.getFlavor())) {
                return candidate.resolve();
            }
        }

        String typeName = type == SharedLibraryBinary.class ? "shared" : "static";
        throw new InvalidUserDataException(String.format("No %s library binary available for %s with %s", typeName, library, flavor));
    }
}
