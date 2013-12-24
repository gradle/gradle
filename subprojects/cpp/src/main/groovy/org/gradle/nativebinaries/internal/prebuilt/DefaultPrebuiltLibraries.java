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

package org.gradle.nativebinaries.internal.prebuilt;

import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.PrebuiltLibraries;
import org.gradle.nativebinaries.PrebuiltLibrary;

public class DefaultPrebuiltLibraries extends AbstractNamedDomainObjectContainer<PrebuiltLibrary> implements PrebuiltLibraries {
    private final FileResolver fileResolver;
    private String name;

    public DefaultPrebuiltLibraries(String name, Instantiator instantiator, FileResolver fileResolver) {
        super(PrebuiltLibrary.class, instantiator);
        this.name = name;
        this.fileResolver = fileResolver;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected PrebuiltLibrary doCreate(String name) {
        return getInstantiator().newInstance(DefaultPrebuiltLibrary.class, name, fileResolver);
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String libraryName) {
        return new UnknownDomainObjectException(String.format("Prebuilt library with name '%s' not found in '%s'.", libraryName, getName()));
    }
}
