/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file.temp

import org.intellij.lang.annotations.Language

class FilePermissionsCheckerFixture {

    static String createFileContents() {
        @Language("groovy")
        String filePermissionsChecker = """
import groovy.transform.CompileStatic

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

@CompileStatic
class FilePermissionsChecker {

    static void assertSafeParentFile(File file) {
        def userHome = new File(System.getProperty("user.home")).toPath()
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // Windows this should always be true
            assert file.toPath().startsWith(userHome) : "Test kit directory must be under the user home for security reasons. Home: '\$userHome', File: '\${file.toPath()}'"
        } else if (file.toPath().startsWith(userHome)) {
            // Most other cases this will be true
            assert file.toPath().startsWith(userHome) : 'Test kit directory must be under the user home for security reasons'
        } else {
            // When run under test distribution, execution will be out of temp, so do a recursive file permissions check
            assertSafePosixPermissions(file)
        }
    }

    private static void assertSafePosixPermissions(File file) {
        Path path = file.toPath()
        Path start
        if (file.exists()) {
            start = path
        } else {
            start = path.parent
        }
        if (!hasSafePosixPermissionsRecursive(start)) {
            String message = "Test kit directory must be under a posix protected directory for security reasons. "+
                "Some parent must not have permissions \${PosixFilePermission.OTHERS_READ} nor \${PosixFilePermission.OTHERS_WRITE}"
            throw new AssertionError(message as Object)
        }
    }

    /**
    * Performs a recursive check of this path and it's parents to find a parent with safe posix permissions.
    * @return true when this directory, or one of it's parent directories has posix file permissions that are deemed safe.
    */
    private static boolean hasSafePosixPermissionsRecursive(Path path) {
        Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(path)
        if (filePermissions.contains(PosixFilePermission.OTHERS_READ) || filePermissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            Path parent = path.parent
            if (parent == null || parent.nameCount == 0) {
                return false
            }
            return hasSafePosixPermissionsRecursive(parent)
        } else {
            return true
        }
    }
}
"""
        return filePermissionsChecker
    }
}
