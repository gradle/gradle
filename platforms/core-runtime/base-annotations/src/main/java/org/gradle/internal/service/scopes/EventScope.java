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
 * Attached to a listener interface to indicate which scope its events are generated in.
 *
 * <p>Events generated in a particular scope are visible to listeners in the same scope and ancestor scopes.
 * Events are not visible to listeners in descendent scopes.
 *
 * <p>This annotation is used primarily to indicate to developers the scopes where this listener are available. There is also
 * some validation when a broadcaster or listener of this type is used in an incorrect scope.
 *
 * @see org.gradle.internal.service.scopes.Scopes
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface EventScope {
    Class<? extends Scope> value();
}
