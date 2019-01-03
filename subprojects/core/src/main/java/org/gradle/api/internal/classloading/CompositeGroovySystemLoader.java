/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.classloading;

import org.gradle.util.CollectionUtils;

import java.util.List;

public class CompositeGroovySystemLoader implements GroovySystemLoader {
    private final List<GroovySystemLoader> loaders;

    public CompositeGroovySystemLoader(GroovySystemLoader... loaders) {
        this.loaders = CollectionUtils.toList(loaders);
    }

    @Override
    public void shutdown() {
        for (GroovySystemLoader loader : loaders) {
            loader.shutdown();
        }
    }

    @Override
    public void discardTypesFrom(ClassLoader classLoader) {
        for (GroovySystemLoader loader : loaders) {
            loader.discardTypesFrom(classLoader);
        }
    }
}
