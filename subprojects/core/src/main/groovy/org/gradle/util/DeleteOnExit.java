/*
 * Copyright 2009 the original author or authors.
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

import org.apache.commons.io.FileUtils;

import java.util.ArrayList;
import java.io.File;

/**
 * Provides a mechanism to delete files or whole directories on shutdown.
 * File.deleteOnExit won't work on subdirectories that are not empty.
 * There are some temporary files which are not currently well managed
 * but we want to make sure that they are eventually removed
 * @author Steve Appling
 */
public class DeleteOnExit {
    private static final ArrayList<File> FILES = new ArrayList<File>();

    static {
        Runtime.getRuntime().addShutdownHook(new DeleteOnExitThread());
    }

    public static void addFile(File file) {
        synchronized (FILES) {
            FILES.add(file);
        }
    }

    private static class DeleteOnExitThread extends Thread {
        public void run() {
            synchronized (FILES) {
                for (File file : FILES) {
                    FileUtils.deleteQuietly(file);
                }
            }
        }
    }
}
