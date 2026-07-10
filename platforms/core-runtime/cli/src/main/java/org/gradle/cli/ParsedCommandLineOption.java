/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.cli;

import java.util.ArrayList;
import java.util.List;

public class ParsedCommandLineOption {
    private final List<String> values = new ArrayList<String>();

    public String getValue() {
        if (!hasValue()) {
            throw new IllegalStateException("Option does not have any value.");
        }
        if (values.size() > 1) {
            throw new IllegalStateException("Option has multiple values.");
        }
        return values.get(0);
    }

    public List<String> getValues() {
        return values;
    }

    public void addArgument(String argument) {
        values.add(argument);
    }

    public boolean hasValue() {
        return !values.isEmpty();
    }
}
