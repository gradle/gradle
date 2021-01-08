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

/**
 * A plugin that could be applied.
 *
 * This may represent an invalid plugin.
 *
 * At the moment it does not encompass plugins that aren't implemented as classes, but it is likely to in the future.
 */
public interface PotentialPlugin<T> {

    enum Type {
        UNKNOWN,
        IMPERATIVE_CLASS,
        PURE_RULE_SOURCE_CLASS,
        HYBRID_IMPERATIVE_AND_RULES_CLASS
    }

    Class<? extends T> asClass();

    boolean isImperative();

    boolean isHasRules();

    Type getType();

}
