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

package org.gradle.internal.authentication;

import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.authentication.Authentication;
import org.gradle.internal.reflect.Instantiator;

public class DefaultAuthenticationContainer extends DefaultPolymorphicDomainObjectContainer<Authentication> implements AuthenticationContainer {

    public DefaultAuthenticationContainer(Instantiator instantiator, CollectionCallbackActionDecorator callbackDecorator) {
        super(Authentication.class, instantiator, instantiator, callbackDecorator);
    }

}
