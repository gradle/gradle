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

package org.gradle.nativeplatform.internal.configure;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.nativeplatform.NativeExecutableSpec;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.NativeComponentExtension;

public class DefaultNativeComponentExtension implements NativeComponentExtension {
    private final NamedDomainObjectContainer<NativeExecutableSpec> executables;
    private final NamedDomainObjectContainer<NativeLibrarySpec> libraries;

    public DefaultNativeComponentExtension(NamedDomainObjectContainer<NativeExecutableSpec> executables, NamedDomainObjectContainer<NativeLibrarySpec> libraries) {
        this.executables = executables;
        this.libraries = libraries;
    }

    public NamedDomainObjectContainer<NativeExecutableSpec> getExecutables() {
        return executables;
    }

    public void executables(Action<? super NamedDomainObjectContainer<? super NativeExecutableSpec>> action) {
        action.execute(executables);
    }

    public NamedDomainObjectContainer<NativeLibrarySpec> getLibraries() {
        return libraries;
    }

    public void libraries(Action<? super NamedDomainObjectContainer<? super NativeLibrarySpec>> action) {
        action.execute(libraries);
    }
}
