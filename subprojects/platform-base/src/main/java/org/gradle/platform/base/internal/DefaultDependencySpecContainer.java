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

package org.gradle.platform.base.internal;

import org.gradle.api.Action;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.LinkedHashSet;

public class DefaultDependencySpecContainer extends LinkedHashSet<DependencySpec> implements DependencySpecContainer {

    @Override
    public DefaultDependencySpec project(final String path) {
        return doCreate(new Action<DependencySpec>() {
            @Override
            public void execute(DependencySpec dependencySpec) {
                dependencySpec.project(path);
            }
        });
    }

    @Override
    public DefaultDependencySpec library(final String name) {
        return doCreate(new Action<DependencySpec>() {
            @Override
            public void execute(DependencySpec dependencySpec) {
                dependencySpec.library(name);
            }
        });
    }

    private DefaultDependencySpec doCreate(Action<? super DependencySpec> action) {
        DefaultDependencySpec spec = new DefaultDependencySpec();
        add(spec);
        action.execute(spec);
        return spec;
    }

}
