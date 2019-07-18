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
package org.gradle.api.distribution.internal;

import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.internal.reflect.Instantiator;

/**
 * Default implementation for {@link org.gradle.api.distribution.DistributionContainer}
 */
public class DefaultDistributionContainer extends AbstractNamedDomainObjectContainer<Distribution> implements DistributionContainer {
    private final FileOperations fileOperations;

    public DefaultDistributionContainer(Class<Distribution> type, Instantiator instantiator, FileOperations fileOperations, CollectionCallbackActionDecorator callbackDecorator) {
        super(type, instantiator, callbackDecorator);
        this.fileOperations = fileOperations;
    }

    @Override
    protected Distribution doCreate(String name) {
        return getInstantiator().newInstance(DefaultDistribution.class, name, fileOperations.copySpec());
    }
}
