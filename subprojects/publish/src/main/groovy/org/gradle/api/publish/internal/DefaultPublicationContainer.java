/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.publish.Publication;
import org.gradle.internal.reflect.Instantiator;

public class DefaultPublicationContainer extends DefaultNamedDomainObjectSet<Publication> implements PublicationContainerInternal {

    private final CompositePublicationFactory publicationFactories = new CompositePublicationFactory();

    public DefaultPublicationContainer(Instantiator instantiator) {
        super(Publication.class, instantiator);
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(Publication o) {
        throw new InvalidUserDataException(String.format("Publication with name '%s' added multiple times", o.getName()));
    }

    public <T extends Publication> T add(String name, Class<T> type) {
        T publication = publicationFactories.create(type, name);
        add(publication);
        return publication;
    }

    public <T extends Publication> T add(String name, Class<T> type, Action<T> action) {
        T publication = add(name, type);
        action.execute(publication);
        return publication;
    }

    public void registerFactory(Class<? extends Publication> type, PublicationFactory publicationFactory) {
        publicationFactories.register(type, publicationFactory);
    }
}
