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

package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.api.Incubating;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.SingleMessageLogger;

import java.util.HashSet;
import java.util.Set;

public class StartParameterInternal extends StartParameter {
    private final Set<String> deprecations = new HashSet<String>();

    @Override
    public StartParameter newInstance() {
        return prepareNewInstance(new StartParameterInternal());
    }

    public StartParameter newBuild() {
        return prepareNewBuild(new StartParameterInternal());
    }

    /**
     * Adds a deprecation item.
     */
    @Incubating
    public void addDeprecation(String deprecation) {
        deprecations.add(deprecation);
    }

    /**
     * Constructs and prints all deprecation warnings.
     */
    @Incubating
    public void checkDeprecation() {
        String suffix = SingleMessageLogger.getDeprecationMessage();
        for (String deprecation : deprecations) {
            DeprecationLogger.nagUserWith(String.format("%s %s.", deprecation, suffix));
        }
    }
}
