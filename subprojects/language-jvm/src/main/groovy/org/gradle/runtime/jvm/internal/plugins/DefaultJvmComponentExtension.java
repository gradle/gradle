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

package org.gradle.runtime.jvm.internal.plugins;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.runtime.jvm.JvmComponentExtension;
import org.gradle.runtime.jvm.ProjectJvmLibrary;

public class DefaultJvmComponentExtension implements JvmComponentExtension {
    private final NamedDomainObjectContainer<ProjectJvmLibrary> libraries;

    public DefaultJvmComponentExtension(NamedDomainObjectContainer<ProjectJvmLibrary> libraries) {
        this.libraries = libraries;
    }

    public NamedDomainObjectContainer<ProjectJvmLibrary> getLibraries() {
        return libraries;
    }

    public void libraries(Action<? super NamedDomainObjectContainer<? super ProjectJvmLibrary>> action) {
        action.execute(libraries);
    }
}
