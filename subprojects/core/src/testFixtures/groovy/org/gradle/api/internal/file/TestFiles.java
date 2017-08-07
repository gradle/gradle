/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.resource.local.FileResourceConnector;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.process.internal.DefaultExecActionFactory;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecHandleFactory;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;

import java.io.File;

public class TestFiles {
    private static final FileSystem FILE_SYSTEM = NativeServicesTestFixture.getInstance().get(FileSystem.class);
    private static final DefaultFileLookup FILE_LOOKUP = new DefaultFileLookup(FILE_SYSTEM, PatternSets.getNonCachingPatternSetFactory());

    public static FileLookup fileLookup() {
        return FILE_LOOKUP;
    }

    public static FileSystem fileSystem() {
        return FILE_SYSTEM;
    }

    public static FileResourceRepository fileRepository() {
        return new FileResourceConnector(FILE_SYSTEM);
    }

    /**
     * Returns a resolver with no base directory.
     */
    public static FileResolver resolver() {
        return FILE_LOOKUP.getFileResolver();
    }

    /**
     * Returns a resolver with the given base directory.
     */
    public static FileResolver resolver(File baseDir) {
        return FILE_LOOKUP.getFileResolver(baseDir);
    }

    public static DirectoryFileTreeFactory directoryFileTreeFactory() {
        return new DefaultDirectoryFileTreeFactory(getPatternSetFactory(), fileSystem());
    }

    public static FileOperations fileOperations(File basedDir) {
        return new DefaultFileOperations(resolver(basedDir), null, null, DirectInstantiator.INSTANCE, fileLookup(), directoryFileTreeFactory());
    }

    public static FileCollectionFactory fileCollectionFactory() {
        return new DefaultFileCollectionFactory();
    }

    public static SourceDirectorySetFactory sourceDirectorySetFactory() {
        return new DefaultSourceDirectorySetFactory(resolver(), new DefaultDirectoryFileTreeFactory());
    }

    public static SourceDirectorySetFactory sourceDirectorySetFactory(File baseDir) {
        return new DefaultSourceDirectorySetFactory(resolver(baseDir), new DefaultDirectoryFileTreeFactory());
    }

    public static ExecActionFactory execActionFactory() {
        return new DefaultExecActionFactory(resolver());
    }

    public static ExecHandleFactory execHandleFactory() {
        return new DefaultExecActionFactory(resolver());
    }

    public static ExecHandleFactory execHandleFactory(File baseDir) {
        return new DefaultExecActionFactory(resolver(baseDir));
    }

    public static JavaExecHandleFactory javaExecHandleFactory(File baseDir) {
        return new DefaultExecActionFactory(resolver(baseDir));
    }

    public static Factory<PatternSet> getPatternSetFactory() {
        return resolver().getPatternSetFactory();
    }

    public static String systemSpecificAbsolutePath(String path) {
        return new File(path).getAbsolutePath();
    }
}
