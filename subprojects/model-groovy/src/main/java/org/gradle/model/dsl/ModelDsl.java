/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.dsl;

import groovy.lang.Closure;
import org.gradle.api.Incubating;

/**
 * Exposes the Groovy level DSL for configuring model elements in a build script.
 */
@Incubating
public interface ModelDsl {

    // TODO - document here the rules for … rules … and explain how `«property path» { }` statements are transformed into calls to this method
    // TODO - allow direct calls to this method? (i.e. model { configure("foo.bar") {} })
    // TODO - prevent rules from calling this method in their implementation - (i.e. model { foo.bar { configure("foo") { } } })
    void configure(String modelPath, Closure<?> configuration);

}
