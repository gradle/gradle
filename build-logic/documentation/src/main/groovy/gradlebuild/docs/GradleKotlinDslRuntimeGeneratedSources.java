/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider;

import javax.inject.Inject;
import java.io.File;

import static com.google.common.collect.Iterables.getOnlyElement;
import static java.util.stream.Collectors.toList;

/**
 * Extracts Kotlin DSL runtime generated sources.
 *
 * Current implementation extracts these from the wrapper's API jars.
 * This is not correct as it should do this with the built distribution instead.
 *
 * Doing it correctly would require running a Gradle build with the full
 * distribution and extracting the generated api jar from its Gradle user home,
 * slowing down building documentation.
 *
 * All this would be so much simpler if the Kotlin extensions to the Gradle API
 * were generated at build time instead.
 *
 * This is a first step to get the doc to be complete and will be revisited.
 */
@CacheableTask
public abstract class GradleKotlinDslRuntimeGeneratedSources extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getInputClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSources();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedClasses();

    @Inject
    protected abstract KotlinScriptClassPathProvider getKotlinScriptClassPathProvider();

    @Inject
    protected abstract ClassLoaderScopeRegistry getClassLoaderScopeRegistry();

    @Inject
    protected abstract ArchiveOperations getArchives();

    @Inject
    protected abstract FileSystemOperations getFs();

    public GradleKotlinDslRuntimeGeneratedSources() {
        getInputClasspath().from(
            ClasspathUtil.getClasspath(getInputClassLoaderScope().getExportClassLoader()).getAsFiles()
        );
    }

    private ClassLoaderScope getInputClassLoaderScope() {
        return getClassLoaderScopeRegistry().getCoreAndPluginsScope();
    }

    @TaskAction
    public void action() {
        FileTree kotlinDslExtensionsJar = getArchives().zipTree(getKotlinDslExtensionsJar());
        getFs().sync(spec -> {
            spec.from(kotlinDslExtensionsJar, zip -> zip.include("**/*.kt"));
            spec.into(getGeneratedSources());
        });
        getFs().sync(spec -> {
            spec.from(kotlinDslExtensionsJar, zip -> zip.include("**/*.class"));
            spec.into(getGeneratedClasses());
        });
    }

    private File getKotlinDslExtensionsJar() {
        return getOnlyElement(
            getKotlinScriptClassPathProvider()
                .compilationClassPathOf(getInputClassLoaderScope())
                .getAsFiles()
                .stream()
                .filter(file -> file.getName().startsWith("gradle-kotlin-dsl-extensions"))
                .collect(toList())
        );
    }
}
