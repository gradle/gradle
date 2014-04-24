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

package org.gradle.nativebinaries.toolchain;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Named;

import java.util.List;

/**
 * An executable tool that forms part of a tool chain.
 */
@Incubating
public interface CommandLineToolConfiguration extends Named {

    /**
     * Adds an action that will be applied to the command-line arguments prior to execution.
     * Remove method with Closure parameter and use Action instead
     */
    void withArguments(Closure arguments);

    /**
     * Adds an action that will be applied to the command-line arguments prior to execution.
     */
    void withArguments(Action<List<String>> arguments);
}
