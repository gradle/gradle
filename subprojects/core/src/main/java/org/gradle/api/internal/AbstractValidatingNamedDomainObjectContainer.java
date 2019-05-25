/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Namer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.NameValidator;

/**
 * A {@link AbstractNamedDomainObjectContainer} that performs name validation before creating a new domain object.
 *
 * @see org.gradle.util.NameValidator#validate(String, String, String)
 */
public abstract class AbstractValidatingNamedDomainObjectContainer<T> extends AbstractNamedDomainObjectContainer<T> {

    private final String nameDescription;

    protected AbstractValidatingNamedDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(type, instantiator, namer, callbackActionDecorator);
        nameDescription = type.getSimpleName() + " name";
    }

    protected AbstractValidatingNamedDomainObjectContainer(Class<T> type, Instantiator instantiator, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(type, instantiator, callbackActionDecorator);
        nameDescription = type.getSimpleName() + " name";
    }

    @Override
    public T create(String name, Action<? super T> configureAction) throws InvalidUserDataException {
        NameValidator.validate(name, nameDescription, "");
        return super.create(name, configureAction);
    }
}
