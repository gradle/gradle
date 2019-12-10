/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.model.internal.core.UnmanagedStruct;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.Set;

/**
 * Specifies the packages that constitute the API of a library. Facilitates separation of
 * API and implementation binaries for that library. Backing object for the
 * {@code api {}} DSL.
 *
 * @since 2.10
 */
@Incubating @UnmanagedStruct
@Deprecated
public interface JvmApiSpec {

    /**
     * Specify a package to be exported as part of the library API.
     *
     * @param packageName the name of the package to be exported, e.g. "com.example.p1"
     * @throws org.gradle.api.InvalidUserDataException if the package name is not valid or has already been exported
     */
    void exports(String packageName);

    /**
     * The set of packages that comprise this library's public API.
     */
    Set<String> getExports();

    /**
     * Specify the dependencies of this API.
     */
    void dependencies(Closure<?> configureAction);

    /**
     * The dependencies of this API.
     */
    DependencySpecContainer getDependencies();
}
