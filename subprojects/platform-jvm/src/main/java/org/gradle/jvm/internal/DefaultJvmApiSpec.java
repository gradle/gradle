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
import org.gradle.api.InvalidUserDataException;
import org.gradle.jvm.JvmApiSpec;
import org.gradle.platform.base.DependencySpecContainer;
import org.gradle.platform.base.internal.DefaultDependencySpecContainer;
import org.gradle.util.ConfigureUtil;

import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;

public class DefaultJvmApiSpec implements JvmApiSpec {

    private final Set<String> exports = new HashSet<String>();
    private final DefaultDependencySpecContainer dependencies = new DefaultDependencySpecContainer();

    @Override
    public void exports(String value) {
        validatePackageName(value);
        if (!exports.add(value)) {
            throw new InvalidUserDataException(
                format("Invalid public API specification: package '%s' has already been exported", value));
        }
    }

    private void validatePackageName(String value) {
        try {
            JvmPackageName.of(value);
        } catch (IllegalArgumentException cause) {
            throw new InvalidUserDataException(
                format("Invalid public API specification: %s", cause.getMessage()), cause);
        }
    }

    @Override
    public Set<String> getExports() {
        return exports;
    }

    @Override
    public DependencySpecContainer getDependencies() {
        return dependencies;
    }

    @Override
    public void dependencies(Closure<?> configureAction) {
        ConfigureUtil.configure(configureAction, dependencies);
    }
}
