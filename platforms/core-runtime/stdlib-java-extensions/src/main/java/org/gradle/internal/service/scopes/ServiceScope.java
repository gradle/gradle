/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.scopes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attached to a service interface to indicate in which scopes it is defined in.
 * Services are lifecycled with their scope, and stopped/closed when the scope is closed.
 * <p>
 * Services are visible to other services in the same scope and descendent scopes.
 * Services are not visible to services in ancestor scopes.
 * <p>
 * When a service is defined in multiple scopes, the highest scope determines the visibility.
 * The additional instances of the service in lower scopes "override" the instance from the parent
 * for their scope and its children.
 *
 * @see Scope
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface ServiceScope {

    /**
     * One or more scopes in which the service is declared,
     * from the longest lifecycle to the shortest.
     */
    Class<? extends Scope>[] value();

}
