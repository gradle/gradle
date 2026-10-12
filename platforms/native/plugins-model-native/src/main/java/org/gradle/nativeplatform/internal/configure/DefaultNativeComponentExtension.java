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

@SuppressWarnings("deprecation")
public class DefaultNativeComponentExtension implements org.gradle.nativeplatform.NativeComponentExtension {
    private final NamedDomainObjectContainer<org.gradle.nativeplatform.NativeExecutableSpec> executables;
    private final NamedDomainObjectContainer<org.gradle.nativeplatform.NativeLibrarySpec> libraries;

    public DefaultNativeComponentExtension(NamedDomainObjectContainer<org.gradle.nativeplatform.NativeExecutableSpec> executables, NamedDomainObjectContainer<org.gradle.nativeplatform.NativeLibrarySpec> libraries) {
        this.executables = executables;
        this.libraries = libraries;
    }

    @Override
    public NamedDomainObjectContainer<org.gradle.nativeplatform.NativeExecutableSpec> getExecutables() {
        return executables;
    }

    @Override
    public void executables(Action<? super NamedDomainObjectContainer<? super org.gradle.nativeplatform.NativeExecutableSpec>> action) {
        action.execute(executables);
    }

    @Override
    public NamedDomainObjectContainer<org.gradle.nativeplatform.NativeLibrarySpec> getLibraries() {
        return libraries;
    }

    @Override
    public void libraries(Action<? super NamedDomainObjectContainer<? super org.gradle.nativeplatform.NativeLibrarySpec>> action) {
        action.execute(libraries);
    }
}
