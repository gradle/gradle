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

package org.gradle.nativeplatform.internal.resolve;

public class LibraryIdentifier {
    private final String projectPath;
    private final String libraryName;

    public LibraryIdentifier(String projectPath, String libraryName) {
        this.libraryName = libraryName;
        this.projectPath = projectPath;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public String toString() {
        return projectPath + ":" + libraryName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        LibraryIdentifier other = (LibraryIdentifier) obj;
        return projectPath.equals(other.projectPath) && libraryName.equals(other.libraryName);
    }

    @Override
    public int hashCode() {
        return projectPath.hashCode() ^ libraryName.hashCode();
    }
}
