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

package org.gradle.internal.jvm;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.specs.Spec;
import org.gradle.cache.internal.FileContentCache;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

@ServiceScope(Scopes.UserHome.class)
public class JavaModuleDetector {

    private final Spec<? super File> classpathFilter = this::isNotModule;
    private final Spec<? super File> modulePathFilter = this::isModule;

    private static final String MODULE_INFO_SOURCE_FILE = "module-info.java";
    private static final String MODULE_INFO_CLASS_FILE = "module-info.class";
    private static final String AUTOMATIC_MODULE_NAME_ATTRIBUTE = "Automatic-Module-Name";
    private static final String MULTI_RELEASE_ATTRIBUTE = "Multi-Release";

    private static final Pattern MODULE_INFO_CLASS_MRJAR_PATH = Pattern.compile("META-INF/versions/\\d+/module-info.class");

    private final FileContentCache<Boolean> cache;
    private final FileCollectionFactory fileCollectionFactory;

    public JavaModuleDetector(FileContentCacheFactory cacheFactory, FileCollectionFactory fileCollectionFactory) {
        this.cache = cacheFactory.newCache("java-modules", 20000, new ModuleInfoLocator(), new BaseSerializerFactory().getSerializerFor(Boolean.class));
        this.fileCollectionFactory = fileCollectionFactory;
    }

    public FileCollection inferClasspath(boolean inferModulePath, Collection<File> classpath) {
        return inferClasspath(inferModulePath, fileCollectionFactory.fixed(classpath));
    }

    public FileCollection inferClasspath(boolean inferModulePath, FileCollection classpath) {
        if (classpath == null) {
            return FileCollectionFactory.empty();
        }
        if (!inferModulePath) {
            return classpath;
        }
        return classpath.filter(classpathFilter);
    }

    public FileCollection inferModulePath(boolean inferModulePath, Collection<File> classpath) {
        return inferModulePath(inferModulePath, fileCollectionFactory.fixed(classpath));
    }

    public FileCollection inferModulePath(boolean inferModulePath, FileCollection classpath) {
        if (classpath == null) {
            return FileCollectionFactory.empty();
        }
        if (!inferModulePath) {
            return FileCollectionFactory.empty();
        }
        return classpath.filter(modulePathFilter);
    }

    public boolean isModule(boolean inferModulePath, FileCollection files) {
        if (!inferModulePath) {
            return false;
        }
        for(File file : files.getFiles()) {
            if (isModule(file)) {
                return true;
            }
        }
        return false;
    }

    public boolean isModule(boolean inferModulePath, File file) {
        if (!inferModulePath) {
            return false;
        }
        return isModule(file);
    }

    private boolean isModule(File file) {
        if (!file.exists()) {
            return false;
        }
        return cache.get(file);
    }

    private boolean isNotModule(File file) {
        if (!file.exists()) {
            return false;
        }
        return !isModule(file);
    }

    public static boolean isModuleSource(boolean inferModulePath, Iterable<File> sourcesRoots) {
        if (!inferModulePath) {
            return false;
        }
        for (File srcFolder : sourcesRoots) {
            if (isModuleSourceFolder(srcFolder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModuleSourceFolder(File folder) {
        return new File(folder, MODULE_INFO_SOURCE_FILE).exists();
    }

    private static class ModuleInfoLocator implements FileContentCacheFactory.Calculator<Boolean> {

        @Override
        public Boolean calculate(File file, boolean isRegularFile) {
            if (isRegularFile) {
                return isJarFile(file) && isModuleJar(file);
            } else {
                return isModuleFolder(file);
            }
        }

        private boolean isJarFile(File file) {
            return file.getName().endsWith(".jar");
        }

        private boolean isModuleFolder(File folder) {
            return new File(folder, MODULE_INFO_CLASS_FILE).exists();
        }

        private boolean isModuleJar(File jarFile) {
            try (JarInputStream jarStream =  new JarInputStream(new FileInputStream(jarFile))) {
                if (containsAutomaticModuleName(jarStream)) {
                    return true;
                }
                boolean isMultiReleaseJar = containsMultiReleaseJarEntry(jarStream);
                ZipEntry next = jarStream.getNextEntry();
                while (next != null) {
                    if (MODULE_INFO_CLASS_FILE.equals(next.getName())) {
                        return true;
                    }
                    if (isMultiReleaseJar && MODULE_INFO_CLASS_MRJAR_PATH.matcher(next.getName()).matches()) {
                        return true;
                    }
                    next = jarStream.getNextEntry();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return false;
        }

        private boolean containsMultiReleaseJarEntry(JarInputStream jarStream) {
            Manifest manifest = jarStream.getManifest();
            return manifest !=null && Boolean.parseBoolean(manifest.getMainAttributes().getValue(MULTI_RELEASE_ATTRIBUTE));
        }

        private boolean containsAutomaticModuleName(JarInputStream jarStream) {
            return getAutomaticModuleName(jarStream.getManifest()) != null;
        }

        private String getAutomaticModuleName(Manifest manifest) {
            if (manifest == null) {
                return null;
            }
            return manifest.getMainAttributes().getValue(AUTOMATIC_MODULE_NAME_ATTRIBUTE);
        }
    }
}
