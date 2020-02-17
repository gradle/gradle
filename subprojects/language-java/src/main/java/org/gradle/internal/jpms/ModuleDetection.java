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

package org.gradle.internal.jpms;

import org.gradle.api.specs.Spec;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModuleDetection {

    public static final Spec<? super File> CLASSPATH_FILTER = (Spec<File>) file -> !isModule(file);
    public static final Spec<? super File> MODULE_PATH_FILTER = (Spec<File>) ModuleDetection::isModule;

    private static final String MODULE_INFO_SOURCE_FILE = "module-info.java";
    private static final String MODULE_INFO_CLASS_FILE = "module-info.class";
    private static final String MANIFEST_LOCATION = "META-INF/MANIFEST.MF";
    private static final String AUTOMATIC_MODULE_NAME_ATTRIBUTE = "Automatic-Module-Name";

    public static boolean isModuleSource(List<File> sourcesRoots) {
        for(File srcFolder : sourcesRoots) {
            if (isModuleSourceFolder(srcFolder)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isModule(File file) {
        if (isJarFile(file)) {
            return isModuleJar(file);
        }
        if (isClassFolder(file)) {
            return isModuleFolder(file);
        }
        return false;
    }

    private static boolean isJarFile(File file) {
        return file.isFile() && file.getName().endsWith(".jar");
    }

    private static boolean isClassFolder(File file) {
        return file.isDirectory();
    }

    private static boolean isModuleFolder(File folder) {
        return new File(folder, MODULE_INFO_CLASS_FILE).exists();
    }

    private static boolean isModuleSourceFolder(File folder) {
        return new File(folder, MODULE_INFO_SOURCE_FILE).exists();
    }

    private static boolean isModuleJar(File jarFile) {
        try (ZipInputStream zipStream =  new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry next = zipStream.getNextEntry();
            while (next != null) {
                if (next.getName().equals(MODULE_INFO_CLASS_FILE)) {
                    return true;
                }
                if (next.getName().equals(MANIFEST_LOCATION)) {
                    return containsAutomaticModuleName(zipStream);
                }
                next = zipStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private static boolean containsAutomaticModuleName(InputStream manifest) throws IOException {
        return new Manifest(manifest).getMainAttributes().getValue(AUTOMATIC_MODULE_NAME_ATTRIBUTE) != null;
    }
}
