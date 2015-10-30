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

package org.gradle.jvm.internal;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.platform.base.DependencySpecContainer;
import org.gradle.platform.base.internal.DefaultDependencySpecContainer;
import org.gradle.util.ConfigureUtil;

import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;

/**
 * Specifies the packages that constitute the API of a library. Facilitates separation of
 * API and implementation binaries for that library. Backing object for the
 * {@code api {}} DSL.
 *
 * @since 2.9
 */
@Incubating
class ApiSpec {

    private final Set<PackageName> exports = new HashSet<PackageName>();
    private final DefaultDependencySpecContainer dependencies = new DefaultDependencySpecContainer();

    /**
     * Specify a package to be exported as part of the library API.
     *
     * @param value the name of the package to be exported
     * @throws InvalidUserDataException if the package name is not valid or has already
     * been exported
     * @see PackageName
     */
    public void exports(String value) {
        final PackageName packageName;
        try {
            packageName = PackageName.of(value);
        } catch (IllegalArgumentException cause) {
            throw new InvalidUserDataException(
                format("Invalid public API specification: %s", cause.getMessage()), cause);
        }
        if (!exports.add(packageName)) {
            throw new InvalidUserDataException(
                format("Invalid public API specification: package '%s' has already been exported", packageName));
        }
    }

    public Set<PackageName> getExports() {
        return exports;
    }

    public DependencySpecContainer getDependencies() {
        return dependencies;
    }

    public void dependencies(Closure<?> configureAction) {
        ConfigureUtil.configure(configureAction, dependencies);
    }
}
