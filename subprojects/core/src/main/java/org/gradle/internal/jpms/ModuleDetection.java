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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.process.ModulePathHandling;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ModuleVisitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class ModuleDetection {

    private static final Spec<? super File> CLASSPATH_FILTER = (Spec<File>) ModuleDetection::isNotModule;
    private static final Spec<? super File> MODULE_PATH_FILTER = (Spec<File>) ModuleDetection::isModule;

    private static final String MODULE_INFO_SOURCE_FILE = "module-info.java";
    private static final String MODULE_INFO_CLASS_FILE = "module-info.class";
    private static final String AUTOMATIC_MODULE_NAME_ATTRIBUTE = "Automatic-Module-Name";

    public static ImmutableList<File> inferClasspath(ModulePathHandling modulePathHandling, FileCollection path) {
        if (path == null) {
            return ImmutableList.of();
        }
        if (modulePathHandling == ModulePathHandling.INFER_MODULE_PATH) {
            return ImmutableList.copyOf(path.filter(CLASSPATH_FILTER));
        }
        if (modulePathHandling == ModulePathHandling.ALL_MODULE_PATH) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(path);
    }

    public static ImmutableList<File> inferClasspath(ModulePathHandling modulePathHandling, Collection<File> path) {
        if (path == null) {
            return ImmutableList.of();
        }
        if (modulePathHandling == ModulePathHandling.INFER_MODULE_PATH) {
            return ImmutableList.copyOf(path.stream().filter(ModuleDetection::isNotModule).collect(Collectors.toList()));
        }
        if (modulePathHandling == ModulePathHandling.ALL_MODULE_PATH) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(path);
    }

    public static ImmutableList<File> inferModulePath(ModulePathHandling modulePathHandling, FileCollection path) {
        if (path == null) {
            return ImmutableList.of();
        }
        if (modulePathHandling == ModulePathHandling.INFER_MODULE_PATH) {
            return ImmutableList.copyOf(path.filter(MODULE_PATH_FILTER));
        }
        if (modulePathHandling == ModulePathHandling.ALL_MODULE_PATH) {
            return ImmutableList.copyOf(path);
        }
        return ImmutableList.of();
    }

    public static ImmutableList<File> inferModulePath(ModulePathHandling modulePathHandling, Collection<File> path) {
        if (path == null) {
            return ImmutableList.of();
        }
        if (modulePathHandling == ModulePathHandling.INFER_MODULE_PATH) {
            return ImmutableList.copyOf(path.stream().filter(ModuleDetection::isModule).collect(Collectors.toList()));
        }
        if (modulePathHandling == ModulePathHandling.ALL_MODULE_PATH) {
            return ImmutableList.copyOf(path);
        }
        return ImmutableList.of();
    }


    public static boolean isModuleSource(Iterable<File> sourcesRoots) {
        for(File srcFolder : sourcesRoots) {
            if (isModuleSourceFolder(srcFolder)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isModuleClasses(Iterable<File> classesRoots) {
        for(File classesFolder : classesRoots) {
            if (isModuleFolder(classesFolder)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> toModuleNames(Collection<File> classpath) {
        return classpath.stream().filter(File::exists).map(ModuleDetection::extractModuleName).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static boolean isClassInModule(String qualifiedName) {
        return qualifiedName.contains("/");
    }

    private static boolean isModule(File file) {
        if (!file.exists()) {
            return false; // TODO this filters empty resource folders; what to do with non-empty ones?
        }
        if (isJarFile(file)) {
            return isModuleJar(file);
        }
        if (isClassFolder(file)) {
            return isModuleFolder(file);
        }
        return false;
    }

    private static boolean isNotModule(File file) {
        if (!file.exists()) {
            return false;
        }
        return !isModule(file);
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
        try (JarInputStream jarStream =  new JarInputStream(new FileInputStream(jarFile))) {
            if (containsAutomaticModuleName(jarStream)) {
                return true;
            }
            ZipEntry next = jarStream.getNextEntry();
            while (next != null) {
                if (next.getName().equals(MODULE_INFO_CLASS_FILE)) {
                    return true;
                }
                next = jarStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private static boolean containsAutomaticModuleName(JarInputStream jarStream) {
        return getAutomaticModuleName(jarStream) != null;
    }

    private static String getAutomaticModuleName(JarInputStream jarStream) {
        return jarStream.getManifest().getMainAttributes().getValue(AUTOMATIC_MODULE_NAME_ATTRIBUTE);
    }

    private static String extractModuleName(File file) {
        if (isClassFolder(file)) {
            return extractModuleNameFromFolder(file);
        } else {
            return extractModuleNameFromJar(file);
        }
    }

    private static String extractModuleNameFromFolder(File folder) {
        File moduleInfoFile = new File(folder, MODULE_INFO_CLASS_FILE);
        if (!moduleInfoFile.exists()) {
            return null;
        }
        try {
            return extractModuleNameFromModuleInfo(new FileInputStream(moduleInfoFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractModuleNameFromJar(File jarFile) {
        try (JarInputStream jarStream =  new JarInputStream(new FileInputStream(jarFile))) {
            String automaticModuleName = getAutomaticModuleName(jarStream);
            if (automaticModuleName != null) {
                return automaticModuleName;
            }
            ZipEntry next = jarStream.getNextEntry();
            while (next != null) {
                if (next.getName().equals(MODULE_INFO_CLASS_FILE)) {
                    return extractModuleNameFromModuleInfo(jarStream);
                }
                next = jarStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private static String extractModuleNameFromModuleInfo(InputStream inputStream) {
        try {
            ClassReader classReader = new ClassReader(inputStream);
            ModuleNameVisitor moduleNameVisitor = new ModuleNameVisitor();
            classReader.accept(moduleNameVisitor, 0);
            return moduleNameVisitor.moduleName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ModuleNameVisitor extends ClassVisitor {
        private String moduleName;

        public ModuleNameVisitor() {
            super(AsmConstants.ASM_LEVEL);
        }

        @Override
        public ModuleVisitor visitModule(String name, int access, String version) {
            moduleName = name;
            return super.visitModule(name, access, version);
        }
    }
}
