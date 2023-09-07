/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.util.internal.JarUtil;

import java.util.OptionalInt;

import static org.gradle.internal.classpath.InstrumentingClasspathFileTransformer.isSupportedVersion;

public class MrJarUtils {
    /**
     * Checks that the given entry is in the versioned directory of the multi-release JAR and this Java version is not yet supported by the instrumentation.
     * The function doesn't check if the entry is actually in the multi-release JAR.
     *
     * @param entry the entry to check
     * @return {@code true} if the entry is in the versioned directory and the Java version isn't supported
     * @see <a href="https://docs.oracle.com/en/java/javase/20/docs/specs/jar/jar.html#multi-release-jar-files">MR JAR specification</a>
     */
    public static boolean isInUnsupportedMrJarVersionedDirectory(ClasspathEntryVisitor.Entry entry) {
        OptionalInt version = JarUtil.getVersionedDirectoryMajorVersion(entry.getName());
        if (version.isPresent()) {
            return !isSupportedVersion(version.getAsInt());
        }
        // The entry is not in the versioned directory at all.
        return false;
    }
}
