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
package org.gradle.nativebinaries.language.c.internal.incremental;

import java.io.File;
import java.io.Serializable;

public class ResolvedInclude implements Serializable {
    private final String include;
    private final File dependencyFile;

    public ResolvedInclude(String include, File dependencyFile) {
        this.include = include;
        this.dependencyFile = dependencyFile;
    }

    public boolean isUnknown() {
        return dependencyFile == null;
    }

    public String getInclude() {
        return include;
    }

    public File getFile() {
        return dependencyFile;
    }

    @Override
    public String toString() {
        return String.format("Resolved include '%s'", include);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResolvedInclude)) {
            return false;
        }

        ResolvedInclude that = (ResolvedInclude) o;

        return include.equals(that.include)
                && (dependencyFile == null ? that.dependencyFile == null : dependencyFile.equals(that.dependencyFile));

    }

    @Override
    public int hashCode() {
        return include.hashCode();
    }
}
