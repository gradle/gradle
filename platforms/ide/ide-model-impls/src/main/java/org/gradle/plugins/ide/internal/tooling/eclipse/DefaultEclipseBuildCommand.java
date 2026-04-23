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

package org.gradle.plugins.ide.internal.tooling.eclipse;

import com.google.common.collect.ImmutableMap;

import java.io.Serializable;
import java.util.Map;

public class DefaultEclipseBuildCommand implements Serializable {

    private String name;
    private Map<String, String> arguments;

    public DefaultEclipseBuildCommand(String name, Map<String, String> arguments) {
        this.name = name;
        this.arguments = ImmutableMap.copyOf(arguments);
    }

    @Override
    public String toString() {
        return "build command '" + name + "'";
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }
}
