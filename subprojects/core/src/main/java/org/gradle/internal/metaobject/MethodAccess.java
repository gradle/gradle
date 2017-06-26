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

package org.gradle.internal.metaobject;

/**
 * Provides dynamic access to methods of some object.
 */
public interface MethodAccess {
    /**
     * Returns true when this object is known to have a method with the given name that accepts the given arguments.
     *
     * <p>Note that not every method is known. Some methods may require an attempt invoke it in order for them to be discovered.</p>
     */
    boolean hasMethod(String name, Object... arguments);

    /**
     * Invokes the method with the given name and arguments.
     */
    DynamicInvokeResult tryInvokeMethod(String name, Object... arguments);
}
