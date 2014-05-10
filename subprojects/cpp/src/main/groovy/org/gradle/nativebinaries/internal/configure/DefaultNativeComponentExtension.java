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

package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.nativebinaries.NativeExecutable;
import org.gradle.nativebinaries.NativeLibrary;
import org.gradle.nativebinaries.NativeComponentExtension;

public class DefaultNativeComponentExtension implements NativeComponentExtension {
    private final NamedDomainObjectContainer<NativeExecutable> executables;
    private final NamedDomainObjectContainer<NativeLibrary> libraries;

    public DefaultNativeComponentExtension(NamedDomainObjectContainer<NativeExecutable> executables, NamedDomainObjectContainer<NativeLibrary> libraries) {
        this.executables = executables;
        this.libraries = libraries;
    }

    public NamedDomainObjectContainer<NativeExecutable> getExecutables() {
        return executables;
    }

    public void executables(Action<? super NamedDomainObjectContainer<? super NativeExecutable>> action) {
        action.execute(executables);
    }

    public NamedDomainObjectContainer<NativeLibrary> getLibraries() {
        return libraries;
    }

    public void libraries(Action<? super NamedDomainObjectContainer<? super NativeLibrary>> action) {
        action.execute(libraries);
    }
}
