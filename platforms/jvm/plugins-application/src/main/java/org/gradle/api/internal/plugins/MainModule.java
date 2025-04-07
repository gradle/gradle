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

import javax.annotation.Nullable;
import java.util.Objects;

public final class MainModule implements AppEntryPoint {
    private final String mainModuleName;
    @Nullable
    private final String mainClassName;

    public MainModule(String mainModuleName, @Nullable String mainClassName) {
        this.mainModuleName = mainModuleName;
        this.mainClassName = mainClassName;
    }

    public String getMainModuleName() {
        return mainModuleName;
    }

    @Nullable
    public String getMainClassName() {
        return mainClassName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MainModule that = (MainModule) o;
        return Objects.equals(mainModuleName, that.mainModuleName) && Objects.equals(mainClassName, that.mainClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mainModuleName, mainClassName);
    }

    @Override
    public String toString() {
        return "MainModule{mainModuleName='" + mainModuleName + ",mainClassName='" + mainClassName + "'}";
    }
}
