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

package org.gradle.language.base.compile;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;
import org.jspecify.annotations.NullMarked;

/**
 * Version of a compiler.
 *
 * @since 4.4
 */
@Incubating
@NullMarked
public interface CompilerVersion {

    /**
     * Returns the type of the compiler.
     */
    @Input
    String getType();

    /**
     * Returns the vendor of the compiler.
     */
    @Input
    String getVendor();

    /**
     * Returns the version of the compiler.
     */
    @Input
    String getVersion();
}
