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
package org.gradle.api.internal.tasks.testing.selection;

import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestSelectionSpec;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class DefaultTestSelectionSpec implements TestSelectionSpec, Serializable {

    private Set<String> names = new HashSet<String>();

    public TestSelectionSpec name(String name) {
        validateName(name);
        names.add(name);
        return this;
    }

    private void validateName(String name) {
        if (name == null || name.length() == 0) {
            throw new InvalidUserDataException("Selected test name cannot be null or empty.");
        }
    }

    @Input
    public Set<String> getNames() {
        return names;
    }

    public TestSelectionSpec setNames(String... names) {
        for (String name : names) {
            validateName(name);
        }
        this.names = Sets.newHashSet(names);
        return this;
    }
}