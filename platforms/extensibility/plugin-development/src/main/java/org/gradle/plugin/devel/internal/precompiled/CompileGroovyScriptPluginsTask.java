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

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.configuration.ScriptTarget;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

@CacheableTask
abstract class CompileGroovyScriptPluginsTask extends DefaultTask {
    private final Provider<Directory> intermediatePluginClassesDirectory;
    private final Provider<Directory> intermediatePluginMetadataDirectory;

    public CompileGroovyScriptPluginsTask() {
        DirectoryProperty buildDir = getProjectLayout().getBuildDirectory();
        this.intermediatePluginClassesDirectory = buildDir.dir("groovy-dsl-plugins/work/classes");
        this.intermediatePluginMetadataDirectory = buildDir.dir("groovy-dsl-plugins/work/metadata");
    }

    @Inject
    abstract protected FileSystemOperations getFileSystemOperations();

    @Inject
    abstract protected ClassLoaderScopeRegistry getClassLoaderScopeRegistry();

    @Inject
    abstract protected ScriptCompilationHandler getScriptCompilationHandler();

    @Inject
    abstract protected CompileOperationFactory getCompileOperationFactory();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Inject
    abstract protected FileSystemAccess getFileSystemAccess();

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getScriptFiles();

    @Classpath
    abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    abstract DirectoryProperty getPrecompiledGroovyScriptsOutputDirectory();

    @Internal
    abstract ListProperty<PrecompiledGroovyScript> getScriptPlugins();

    @TaskAction
    void compileScripts() {
        ClassLoaderScope classLoaderScope = getClassLoaderScopeRegistry().getCoreAndPluginsScope();
        ClassPath classPath = DefaultClassPath.of(getClasspath());
        ClassLoader compileClassLoader = new ContentKeyedClassLoader(
            classPath.getAsURLArray(),
            classLoaderScope.getLocalClassLoader(),
            hashClasspathContent(classPath)
        );
        FileSystemOperations fileSystemOperations = getFileSystemOperations();
        fileSystemOperations.delete(spec -> spec.delete(intermediatePluginMetadataDirectory, intermediatePluginClassesDirectory));
        intermediatePluginMetadataDirectory.get().getAsFile().mkdirs();
        intermediatePluginClassesDirectory.get().getAsFile().mkdirs();

        // TODO: Use worker API?
        for (PrecompiledGroovyScript scriptPlugin : getScriptPlugins().get()) {
            compileBuildScript(scriptPlugin, compileClassLoader);
        }

        fileSystemOperations.sync(copySpec -> {
            copySpec.from(intermediatePluginClassesDirectory.get().getAsFileTree().getFiles());
            copySpec.into(getPrecompiledGroovyScriptsOutputDirectory());
        });
        ClassLoaderUtils.tryClose(compileClassLoader);
    }

    private void compileBuildScript(PrecompiledGroovyScript scriptPlugin, ClassLoader compileClassLoader) {
        ScriptTarget target = scriptPlugin.getScriptTarget();
        CompileOperation<BuildScriptData> scriptCompileOperation = getCompileOperationFactory().getScriptCompileOperation(scriptPlugin.getBodySource(), target);
        File scriptMetadataDir = subdirectory(intermediatePluginMetadataDirectory, scriptPlugin.getId());
        File scriptClassesDir = subdirectory(intermediatePluginClassesDirectory, scriptPlugin.getId());
        getScriptCompilationHandler().compileToDir(
            scriptPlugin.getBodySource(), compileClassLoader, scriptClassesDir,
            scriptMetadataDir, scriptCompileOperation, target.getScriptClass(),
            ClosureCreationInterceptingVerifier.INSTANCE);
    }

    /**
     * Combines the root hashes of the classpath entries. The file system access already snapshotted these entries when
     * fingerprinting the task inputs, so this does not hash any files again.
     */
    private String hashClasspathContent(ClassPath classPath) {
        Hasher hasher = Hashing.newHasher();
        FileSystemAccess fileSystemAccess = getFileSystemAccess();
        for (File file : classPath.getAsFiles()) {
            hasher.putHash(fileSystemAccess.read(file.getAbsolutePath()).getHash());
        }
        return hasher.hash().toString();
    }

    private static File subdirectory(Provider<Directory> root, String subdirPath) {
        return root.get().dir(subdirPath).getAsFile();
    }

    /**
     * Groovy's {@code AsmDecompiler} keeps a JVM-wide cache of class stubs keyed by the resource URI, but doesn't check
     * whether the resource changed. The classpath of this task points at jars and directories that can be rebuilt in
     * place, so in a warm daemon the compiler would see stale signatures. To avoid that, every resource URL from this
     * loader gets the classpath's content hash as a fragment. The cache key then changes automatically only when the
     * classpath content changes.
     *
     * @see <a href="https://issues.apache.org/jira/browse/GROOVY-12682">GROOVY-12682</a>
     */
    private static final class ContentKeyedClassLoader extends URLClassLoader {
        static {
            ClassLoader.registerAsParallelCapable();
        }

        private final String fragment;

        ContentKeyedClassLoader(URL[] urls, ClassLoader parent, String fragment) {
            super(urls, parent);
            this.fragment = fragment;
        }

        @Override
        public @Nullable URL findResource(String name) {
            URL url = super.findResource(name);
            if (url == null || url.getRef() != null) {
                return url;
            }
            try {
                return new URL(url.toExternalForm() + "#" + fragment);
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
