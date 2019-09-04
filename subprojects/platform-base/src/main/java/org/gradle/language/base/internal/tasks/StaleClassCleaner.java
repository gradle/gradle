/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.language.base.internal.tasks;

import org.gradle.internal.file.Deleter;

import java.io.File;
import java.util.Collections;

public abstract class StaleClassCleaner {
    public abstract void execute();

    public abstract void addDirToClean(File toClean);

    public static boolean cleanOutputs(Deleter deleter, Iterable<File> filesToDelete, File directory) {
        return cleanOutputs(deleter, filesToDelete, Collections.singleton(directory));
    }

    public static boolean cleanOutputs(Deleter deleter, Iterable<File> filesToDelete, Iterable<File> directories) {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(deleter, filesToDelete);
        directories.forEach(cleaner::addDirToClean);
        cleaner.execute();
        return cleaner.getDidWork();
    }
}
