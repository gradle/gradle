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
package org.gradle.language.nativeplatform.internal.incremental;

import java.io.File;

public class ResolvedInclude {
    private final String include;
    private final File dependencyFile;

    public ResolvedInclude(String include, File dependencyFile) {
        this.include = include;
        this.dependencyFile = dependencyFile;
    }

    public boolean isUnknown() {
        return dependencyFile == null;
    }

    public boolean isMaybeMacro() {
        // TODO: Someone could be evil and include a file called "validIdentifier"
        return isUnknown() && isMacro(include);
    }

    private static boolean isMacro(String token) {
        if (token.isEmpty()
            || !Character.isJavaIdentifierStart(token.charAt(0))) {
            return false;
        }
        if (token.length() > 1) {
            for (char c : token.substring(1).toCharArray()) {
                if (!Character.isJavaIdentifierPart(c)) {
                    return false;
                }
            }
        }
        return true;
    }

    public String getInclude() {
        return include;
    }

    public File getFile() {
        return dependencyFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ResolvedInclude that = (ResolvedInclude) o;

        if (!include.equals(that.include)) {
            return false;
        }
        return !(dependencyFile != null ? !dependencyFile.equals(that.dependencyFile) : that.dependencyFile != null);

    }

    @Override
    public int hashCode() {
        return include.hashCode();
    }

    @Override
    public String toString() {
        return "Resolved include '" + include + "' -> '" + resolved() + "'";
    }

    private String resolved() {
        if (isUnknown()) {
            return "???";
        }
        return dependencyFile.getAbsolutePath();
    }

}
