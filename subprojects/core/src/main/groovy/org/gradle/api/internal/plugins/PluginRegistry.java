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

package org.gradle.api.internal.plugins;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.api.internal.initialization.ClassLoaderScope;

@ThreadSafe
public interface PluginRegistry {

    // Note: the methods taking a String 'idOrName' may implicitly resolve the name to an ID
    //       in such cases, the return value will convey the fully qualified ID

    <T> PotentialPlugin<T> inspect(Class<T> clazz);

    @Nullable
    PotentialPluginWithId lookup(String idOrName);

    @Nullable
    PotentialPluginWithId lookup(String idOrName, ClassLoader classLoader);

    PluginRegistry createChild(ClassLoaderScope lookupScope);

}
