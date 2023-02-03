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

package org.gradle.api.internal.classloading;

public interface GroovySystemLoader {
    /**
     * Invoked when this Groovy system is to be discarded, so that the Groovy system can remove any static state it may have registered in other ClassLoaders.
     */
    void shutdown();

    /**
     * Invoked when another ClassLoader is discarded, so that this Groovy system can remove state for the classes loaded from the ClassLoader
     */
    void discardTypesFrom(ClassLoader classLoader);
}
