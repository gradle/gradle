/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.build.docs

import groovy.transform.CompileStatic
import org.asciidoctor.gradle.AsciidoctorTask
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.util.PatternSet
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec

@CacheableTask
@CompileStatic
class CacheableAsciidoctorTask extends AsciidoctorTask {

    private ProjectInternal decoratedProject

    @Input
    ListProperty<String> jvmArgs = super.project.objects.listProperty(String).convention([])

    @Internal
    @Override
    List<Object> getAsciidoctorExtensions() {
        return super.getAsciidoctorExtensions()
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    File getBaseDir() {
        return super.getBaseDir()
    }

    @Classpath
    @Optional
    @Override
    Configuration getClasspath() {
        return super.getClasspath()
    }

    @Internal
    @Override
    CopySpec getDefaultResourceCopySpec() {
        return super.getDefaultResourceCopySpec()
    }

    @Internal
    @Override
    PatternSet getDefaultSourceDocumentPattern() {
        return super.getDefaultSourceDocumentPattern()
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    FileCollection getGemPath() {
        return super.getGemPath()
    }

    @Input
    @Override
    boolean getLogDocuments() {
        return super.getLogDocuments()
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Note on cacheability:</b>
     * AsciidoctorTask annotates getOutputDirectories() with @OutputDirectories which returns a Set which breaks cacheability.
     * Since we use `separateOutputDirs = false`, getOutputDir() is sufficient and we can just ignore getOutputDirectories().
     * </p>
     */
    @Override
    @Internal
    Set<File> getOutputDirectories() {
        super.getOutputDirectories()
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Note on cacheability:</b>
     * We don't use this. Otherwise it should be tracked in some way.
     * </p>
     */
    @Internal
    @Override
    CopySpec getResourceCopySpec() {
        return super.getResourceCopySpec()
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    File getSourceDir() {
        super.getSourceDir()
    }

    @Internal
    @Override
    File getSourceDocumentName() {
        return super.getSourceDocumentName()
    }

    @Internal
    @Override
    FileCollection getSourceDocumentNames() {
        return super.getSourceDocumentNames()
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSourceFileTree() {
        super.getSourceFileTree()
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getResourceFileCollection() {
        super.getResourceFileCollection()
    }

    @Internal
    @Override
    Project getProject() {
        if (decoratedProject == null) {
            decoratedProject = new ProjectDecorator((ProjectInternal) super.project)
        }
        decoratedProject
    }

    class ProjectDecorator implements ProjectInternal {
        final @Delegate
        ProjectInternal delegate

        ProjectDecorator(ProjectInternal delegate) {
            this.delegate = delegate
        }

        // override to make the Groovy compiler happy
        PluginManagerInternal getPluginManager() {
            (PluginManagerInternal) delegate.pluginManager
        }

        @Override
        ExecResult javaexec(Action<? super JavaExecSpec> action) {
            delegate.javaexec(new Action<JavaExecSpec>() {
                @Override
                void execute(JavaExecSpec javaExecSpec) {
                    javaExecSpec.jvmArgs(jvmArgs.get())
                    action.execute(javaExecSpec)
                }
            })
        }

        @Override
        ExecResult javaexec(Closure action) {
            delegate.javaexec { JavaExecSpec spec ->
                spec.jvmArgs(jvmArgs.get())
                action(spec)
            }
        }

        boolean equals(o) {
            if (this.is(o)) {
                return true
            }
            if (getClass() != o.class) {
                return false
            }

            ProjectDecorator that = (ProjectDecorator) o

            if (delegate != that.delegate) {
                return false
            }

            return true
        }

        int hashCode() {
            return delegate.hashCode()
        }
    }
}
