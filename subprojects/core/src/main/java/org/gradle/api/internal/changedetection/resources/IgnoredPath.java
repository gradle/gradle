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

package org.gradle.api.internal.changedetection.resources;

public class IgnoredPath implements NormalizedPath {
    private static final IgnoredPath INSTANCE = new IgnoredPath();

    public static IgnoredPath getInstance() {
        return INSTANCE;
    }

    private IgnoredPath() {
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public int compareTo(NormalizedPath o) {
        if (!(o instanceof IgnoredPath)) {
            return -1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return 1;
    }
}
