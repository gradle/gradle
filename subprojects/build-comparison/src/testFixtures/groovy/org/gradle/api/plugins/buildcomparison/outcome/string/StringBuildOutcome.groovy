/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.outcome.string

import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome

class StringBuildOutcome implements BuildOutcome {

    final String name
    final String value

    StringBuildOutcome(String name, String value) {
        this.name = name
        this.value = value
    }

    String getDescription() {
        "string: $value"
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        StringBuildOutcome that = (StringBuildOutcome) o

        if (name != that.name) {
            return false
        }
        if (value != that.value) {
            return false
        }

        return true
    }

    int hashCode() {
        int result
        result = (name != null ? name.hashCode() : 0)
        result = 31 * result + (value != null ? value.hashCode() : 0)
        return result
    }

    @Override
    public String toString() {
        return "StringBuildOutcome{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
