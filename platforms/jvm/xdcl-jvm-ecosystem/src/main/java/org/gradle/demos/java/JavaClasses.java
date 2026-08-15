/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.java;

import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;

/**
 * The build-side outputs of one named source set.  A fully-managed {@link Named} type so it works as the element of a
 * {@code NamedDomainObjectContainer} auto-created on {@link JavaLibraryModel#getClasses()}.
 */
public interface JavaClasses extends Named {

    /**
     * The compiler output for this source set (where the {@code compile<Name>Java} task writes) —
     * the bytecode the {@code jar} and {@code test} tasks consume.
     */
    DirectoryProperty getClassesDir();

    DirectoryProperty getProcessedResourcesDir();
}
