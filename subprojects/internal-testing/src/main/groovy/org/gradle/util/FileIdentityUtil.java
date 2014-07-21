/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.util;

import org.gradle.internal.jvm.Jvm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class FileIdentityUtil {
    private final static Logger LOGGER = LoggerFactory.getLogger(FileIdentityUtil.class);

    public static boolean isSameFile(File file1, File file2) throws IOException {
        FileIdentityChecker checker = null;
        if (Jvm.current().getJavaVersion().isJava7Compatible()) {
            String jdkFilePermissionclass = "org.gradle.util.jdk7.Jdk7FileIdentityChecker";
            Class<?> jdk7FileChecker = null;
            try {
                jdk7FileChecker = FileIdentityUtil.class.getClassLoader().loadClass(jdkFilePermissionclass);
            } catch (ClassNotFoundException e) {
                LOGGER.warn(String.format("Unable to load %s. Continuing with fallback.", jdkFilePermissionclass));
            }
            if (jdk7FileChecker != null) {
                try {
                    checker = (FileIdentityChecker) jdk7FileChecker.newInstance();
                } catch (Exception e) {
                    LOGGER.warn(String.format("Unable to instantiate %s", jdk7FileChecker));
                }
            }
        }
        if (checker == null) {
            checker = new SimpleFileIdentiyChecker();
        }
        return checker.isSameFile(file1, file2);
    }

    private static class SimpleFileIdentiyChecker implements FileIdentityChecker {
        public boolean isSameFile(File file1, File file2) throws IOException {
            return file1.getCanonicalPath().equals(file2.getCanonicalPath());
        }
    }
}
