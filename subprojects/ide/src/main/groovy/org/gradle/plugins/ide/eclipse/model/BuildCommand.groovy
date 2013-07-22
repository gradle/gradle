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
package org.gradle.plugins.ide.eclipse.model

class BuildCommand implements Serializable {
    String name
    Map arguments

    def BuildCommand(String name, Map arguments = [:]) {
        assert name != null
        assert arguments != null
        this.name = name
        this.arguments = arguments
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        BuildCommand that = (BuildCommand) o;

        if (arguments != that.arguments) { return false }
        if (name != that.name) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = name.hashCode();
        result = 31 * result + arguments.hashCode();
        return result;
    }


    public String toString() {
        return "BuildCommand{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
