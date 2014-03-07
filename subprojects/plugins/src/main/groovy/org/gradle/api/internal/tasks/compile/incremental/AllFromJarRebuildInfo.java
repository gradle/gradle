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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileTree;

import java.util.Set;

public class AllFromJarRebuildInfo extends DefaultRebuildInfo {
    AllFromJarRebuildInfo(FileTree jarContents) {
        super(false, classesInJar(jarContents));
    }

    private static Set<String> classesInJar(FileTree jarContents) {
        throw new RuntimeException("not implemented yet");
//        Set<String> classes = new HashSet<String>();
//        for (File file : jarContents) {
//            if (file.getName().endsWith(".class")) {
//                classes.add(file.getName().replaceAll("/", ".").substring() replaceAll("\\.class", "") )
//            }
//        }
//        return classes;
    }
}
