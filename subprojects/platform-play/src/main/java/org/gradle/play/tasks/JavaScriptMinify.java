/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.language.base.internal.tasks.StaleClassCleaner;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.play.internal.javascript.DefaultJavaScriptCompileSpec;
import org.gradle.play.internal.javascript.JavaScriptCompileSpec;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.toolchain.PlayToolChain;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * Task to minify JavaScript assets.
 */
@Incubating
public class JavaScriptMinify extends SourceTask {
    private File destinationDir;
    private PlayPlatform playPlatform;
    private BaseForkOptions forkOptions;

    public JavaScriptMinify() {
        this.include("**/*.js");
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the tool chain that will be used to compile the JavaScript source.
     *
     * @return The tool chain.
     */
    @Inject
    public PlayToolChain getToolChain() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the output directory that processed JavaScript is written to.
     *
     * @return The output directory.
     */
    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    /**
     * Sets the output directory where processed JavaScript should be written.
     *
     * @param destinationDir The output directory.
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * Sets the target Play platform.
     *
     * @param playPlatform The target Play platform.
     */
    public void setPlayPlatform(PlayPlatform playPlatform) {
        this.playPlatform = playPlatform;
    }

    @Internal
    private Compiler<JavaScriptCompileSpec> getCompiler() {
        ToolProvider select = ((PlayToolChainInternal) getToolChain()).select(playPlatform);
        return select.newCompiler(JavaScriptCompileSpec.class);
    }

    /**
     * The fork options to be applied to the JavaScript compiler.
     *
     * @return The fork options for the JavaScript compiler.
     */
    @Nested
    public BaseForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new BaseForkOptions();
        }
        return forkOptions;
    }

    @TaskAction
    void compileJavaScriptSources() {
        StaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getDestinationDir());
        cleaner.execute();

        MinifyFileVisitor visitor = new MinifyFileVisitor();
        getSource().visit(visitor);

        JavaScriptCompileSpec spec = new DefaultJavaScriptCompileSpec(visitor.relativeFiles, getDestinationDir(), getForkOptions());
        getCompiler().execute(spec);
    }

    /**
     * Copies each file in the source set to the output directory and gathers relative files for compilation
     */
    class MinifyFileVisitor implements FileVisitor {
        List<RelativeFile> relativeFiles = Lists.newArrayList();

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            new File(destinationDir, dirDetails.getRelativePath().getPathString()).mkdirs();
        }

        @Override
        public void visitFile(final FileVisitDetails fileDetails) {
            final File outputFileDir = new File(destinationDir, fileDetails.getRelativePath().getParent().getPathString());

            // Copy the raw form
            FileOperations fileOperations = (ProjectInternal) getProject();
            fileOperations.copy(new Action<CopySpec>() {
                @Override
                public void execute(CopySpec copySpec) {
                    copySpec.from(fileDetails.getFile()).into(outputFileDir);
                }
            });

            // Capture the relative file
            relativeFiles.add(new RelativeFile(fileDetails.getFile(), fileDetails.getRelativePath()));
        }
    }
}
