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

package org.gradle.internal.component.external.descriptor;

/**
 * A "scope" taken from a Maven POM. Note that the order of this enum is important for the module metadata cache.
 */
public enum MavenScope {
    Compile("compile"),
    Runtime("runtime"),
    Provided("provided"),
    Test("test"),
    System("system");

    private final String lowerName;

    MavenScope(String lowerName) {
        this.lowerName = lowerName;
    }

    public String getLowerName() {
        return lowerName;
    }
}
