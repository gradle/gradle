/*
 * Copyright 2026 the original author or authors.
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


package org.gradle.problems.internal.services;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Tells whether a jar or class directory holds a class. The entry names of each jar are read once and kept.
 */
class ClassFileLookup {

    private final Map<File, Set<String>> entriesByJar = new ConcurrentHashMap<>();

    boolean contains(File codeLocation, String className) {
        String classFile = className.replace('.', '/') + ".class";
        if (codeLocation.isDirectory()) {
            return new File(codeLocation, classFile).isFile();
        }
        return entriesByJar.computeIfAbsent(codeLocation, ClassFileLookup::entriesOf).contains(classFile);
    }

    private static Set<String> entriesOf(File jar) {
        ImmutableSet.Builder<String> entries = ImmutableSet.builder();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                String name = all.nextElement().getName();
                if (name.endsWith(".class")) {
                    entries.add(name);
                }
            }
        } catch (IOException e) {
            return ImmutableSet.of();
        }
        return entries.build();
    }
}
