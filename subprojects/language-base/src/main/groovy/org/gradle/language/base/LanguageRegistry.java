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

package org.gradle.language.base;

/**
 * A registry of domains.
 * TODO:DAZ This should be done by inspecting the plugin, rather than having the plugin register explicitly.
 */
public interface LanguageRegistry {
    /**
     * Register a supported language.
     */
    <U extends LanguageSourceSet, V extends U>  void registerLanguage(String name, Class<U> sourceType, Class<V> sourceImplementation);

}
