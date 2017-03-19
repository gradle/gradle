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

package org.gradle.api.reflect;

import org.gradle.api.Incubating;

/**
 * Allows a scriptable object, such as a project extension, to declare its preferred public type.
 *
 * The public type of an object is the one exposed to statically-typed consumers, such as Kotlin build scripts, by default.
 *
 * @since 3.5
 */
@Incubating
public interface HasPublicType {

    /**
     * Public type.
     *
     * @return this object's public type
     */
    TypeOf<?> getPublicType();
}
