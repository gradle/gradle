/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.reflect;

import java.lang.reflect.Method;
import java.util.List;

public interface PropertyDetails {

    String getName();

    /**
     * The getter methods for a particular type.
     *
     * This list will only ever contain more than one method when there are getter methods with <b>different</b> return types.
     * If a getter is declared multiple times by this type (through inheritance) with identical return types, only one method object will be present for the type.
     *
     * As an equivalent getter can be declared multiple times (e.g in a super class, overridden by the target type).
     * The actual method instance used is significant.
     * The method instance is for the “nearest” declaration, where classes take precedence over interfaces.
     * Interface precedence is breadth-first, declaration.
     *
     * The order of the methods follows the same precedence.
     * That is, the “nearer” declarations are earlier in the list.
     */
    List<Method> getGetters();

    /**
     * Similar to {@link #getGetters()}, but varies based on the param type instead of return type.
     *
     * Has identical ordering semantics.
     */
    List<Method> getSetters();
}
