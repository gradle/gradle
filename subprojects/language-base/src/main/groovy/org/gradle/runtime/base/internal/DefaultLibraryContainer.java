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

package org.gradle.runtime.base.internal;

import org.gradle.api.Named;
import org.gradle.api.Namer;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.runtime.base.Library;
import org.gradle.runtime.base.LibraryContainer;

public class DefaultLibraryContainer extends DefaultPolymorphicDomainObjectContainer<Library> implements LibraryContainer {

    public DefaultLibraryContainer(Instantiator instantiator) {
        super(Library.class, instantiator, new LibraryNamer());
    }

    // TODO:DAZ Not sure if Library should extend Named?
    private static class LibraryNamer implements Namer<Library> {
        public String determineName(Library library) {
            if (library instanceof Named) {
                return ((Named) library).getName();
            }
            throw new IllegalArgumentException(String.format("Library %s cannot be added to LibraryContainer as it is does not implement %s.", library, Named.class.getName()));
        }
    }
}
