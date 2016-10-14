/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.service.DefaultServiceLocator;

import java.util.Collection;

class GradleApiSpecAggregator {

    private final ClassLoader classLoader;

    public GradleApiSpecAggregator(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public GradleApiSpecProvider.Spec aggregate() {
        GradleApiSpecProvider.SpecBuilder builder = new GradleApiSpecProvider.SpecBuilder();
        for (Class<? extends GradleApiSpecProvider> provider : providers()) {
            builder.merge(specFrom(provider));
        }
        return builder.build();
    }

    private GradleApiSpecProvider.Spec specFrom(Class<? extends GradleApiSpecProvider> provider) {
        return instantiate(provider).get();
    }

    private Collection<Class<? extends GradleApiSpecProvider>> providers() {
        return new DefaultServiceLocator(classLoader).implementationsOf(GradleApiSpecProvider.class);
    }

    private GradleApiSpecProvider instantiate(Class<? extends GradleApiSpecProvider> providerClass) {
        return DirectInstantiator.INSTANCE.newInstance(providerClass);
    }
}
