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

package org.gradle.integtests.fixtures.versions;

import org.gradle.util.GradleVersion;

public class ReleasedGradleVersion {

    private final GradleVersion version;
    private final Type type;
    private final boolean current;

    public ReleasedGradleVersion(GradleVersion version, Type type, boolean current) {
        this.version = version;
        this.type = type;
        this.current = current;
    }

    @Override
    public String toString() {
        return "ReleasedGradleVersion{"
                + "version=" + version
                + ", type=" + type
                + ", current=" + current
                + '}';
    }

    public GradleVersion getVersion() {
        return version;
    }

    public Type getType() {
        return type;
    }

    public boolean isCurrent() {
        return current;
    }

    public static enum Type {
        NIGHTLY,
        RELEASE_CANDIDATE,
        FINAL
    }
}
