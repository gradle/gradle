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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.DependencySpecBuilder;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DefaultDependencySpecContainer implements DependencySpecContainer {

    private final List<DefaultDependencySpec.Builder> builders = new LinkedList<DefaultDependencySpec.Builder>();

    @Override
    public DependencySpecBuilder project(final String path) {
        return doCreate(new Action<DefaultDependencySpec.Builder>() {
            @Override
            public void execute(DefaultDependencySpec.Builder builder) {
                builder.project(path);
            }
        });
    }

    @Override
    public DependencySpecBuilder library(final String name) {
        return doCreate(new Action<DefaultDependencySpec.Builder>() {
            @Override
            public void execute(DefaultDependencySpec.Builder builder) {
                builder.library(name);
            }
        });
    }

    public Collection<DependencySpec> getDependencies() {
        if (builders.isEmpty()) {
            return Collections.emptySet();
        }
        return ImmutableSet.copyOf(Lists.transform(builders, new Function<DefaultDependencySpec.Builder, DependencySpec>() {
            @Override
            public DependencySpec apply(DefaultDependencySpec.Builder builder) {
                return builder.build();
            }
        }));
    }

    private DefaultDependencySpec.Builder doCreate(Action<? super DefaultDependencySpec.Builder> action) {
        DefaultDependencySpec.Builder builder = new DefaultDependencySpec.Builder();
        action.execute(builder);
        builders.add(builder);
        return builder;
    }

    @Override
    public boolean isEmpty() {
        return builders.isEmpty();
    }
}
