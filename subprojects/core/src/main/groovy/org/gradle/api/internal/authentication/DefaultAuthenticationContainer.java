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

package org.gradle.api.internal.authentication;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.authentication.Authentication;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.internal.reflect.Instantiator;

public class DefaultAuthenticationContainer extends DefaultPolymorphicDomainObjectContainer<Authentication> implements AuthenticationContainer {

    public DefaultAuthenticationContainer(Instantiator instantiator) {
        super(Authentication.class, instantiator);
    }

    @Override
    public boolean add(Authentication o) {
        assertCanAdd(o);
        return super.add(o);
    }

    @Override
    protected void assertCanAdd(Authentication authentication) {
        super.assertCanAdd(authentication);

        Class authType = new DslObject(authentication).getDeclaredType();

        for (Authentication existing : getStore()) {
            Class existingType = new DslObject(existing).getDeclaredType();

            if (authType.equals(existingType)) {
                throw new InvalidUserDataException(String.format("Cannot configure multiple authentication schemes of type '%s'", authType.getSimpleName()));
            }
        }
    }
}
