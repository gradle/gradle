/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.plugins;

import java.util.Objects;

public final class MainClass implements AppEntryPoint {
    private final String mainClassName;

    public MainClass(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MainClass mainClass = (MainClass) o;
        return Objects.equals(mainClassName, mainClass.mainClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mainClassName);
    }

    @Override
    public String toString() {
        return "MainClass{mainClassName='" + mainClassName + "'}";
    }
}
