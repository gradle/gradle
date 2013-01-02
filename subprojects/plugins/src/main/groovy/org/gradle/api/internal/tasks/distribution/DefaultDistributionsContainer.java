/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks.distribution;

import groovy.lang.Closure;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.tasks.distribution.Distribution;
import org.gradle.api.tasks.distribution.DistributionsContainer;
import org.gradle.internal.reflect.Instantiator;

/**
 * Default implementation for {@link org.gradle.api.tasks.distribution.DistributionsContainer}
 * @author scogneau
 */
public class DefaultDistributionsContainer extends AbstractNamedDomainObjectContainer<Distribution> implements DistributionsContainer {
    protected DefaultDistributionsContainer(Class<Distribution> type, Instantiator instantiator) {
        super(type, instantiator);
    }

    @Override
    protected Distribution doCreate(String name) {
        return new DefaultDistribution(name);
    }

    @Override
    public Distribution add(String name) {
        return doCreate(name);
    }

    @Override
    public Distribution add(String name, Closure configurationClosure) {
        return create(name, configurationClosure);
    }

}
