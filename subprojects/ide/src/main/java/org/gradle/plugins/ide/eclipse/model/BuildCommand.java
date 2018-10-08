/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.util.Map;

/**
 * A build command.
 */
public class BuildCommand implements Serializable {
    private String name;
    private Map<String, String> arguments;

    public BuildCommand(String name) {
        this(name, Maps.<String, String>newLinkedHashMap());
    }

    public BuildCommand(String name, Map<String, String> arguments) {
        this.name = Preconditions.checkNotNull(name);
        this.arguments = Preconditions.checkNotNull(arguments);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildCommand that = (BuildCommand) o;
        return Objects.equal(name, that.name) && Objects.equal(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, arguments);
    }

    @Override
    public String toString() {
        return "BuildCommand{name='" + name + "', arguments=" + arguments + "}";
    }
}
