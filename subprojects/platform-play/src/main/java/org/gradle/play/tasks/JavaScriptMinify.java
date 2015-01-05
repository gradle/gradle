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

import com.beust.jcommander.internal.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.language.base.internal.tasks.StaleClassCleaner;
import org.gradle.plugins.javascript.base.SourceTransformationException;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.JavaExecAction;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * Task to minify JavaScript assets.
 */
@Incubating
public class JavaScriptMinify extends SourceTask {
    private File destinationDir;
    private Object closureCompilerClasspath;

    private static final String DEFAULT_GOOGLE_CLOSURE_VERSION = "v20141215";

    static String getDefaultGoogleClosureNotation() {
        return String.format("com.google.javascript:closure-compiler:%s", DEFAULT_GOOGLE_CLOSURE_VERSION);
    }

    public JavaScriptMinify() {
        setClosureCompilerNotation(getDefaultGoogleClosureNotation());
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    @InputFiles
    public Object getClosureCompilerClasspath() {
        return getProject().files(closureCompilerClasspath);
    }

    public void setClosureCompilerClasspath(Object closureCompilerClasspath) {
        this.closureCompilerClasspath = closureCompilerClasspath;
    }

    public void setClosureCompilerNotation(String notation) {
        setClosureCompilerClasspath(getDetachedConfiguration(notation));
    }

    private Configuration getDetachedConfiguration(String notation) {
        Dependency dependency = getProject().getDependencies().create(notation);
        return getProject().getConfigurations().detachedConfiguration(dependency);
    }

    @TaskAction
    void compileJavaScriptSources() {
        StaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getDestinationDir());
        cleaner.execute();
        MinifyFileVisitor visitor = new MinifyFileVisitor(getClosureCompilerClasspath());
        getSource().visit(visitor);
        if (visitor.hasFailures) {
            throw new SourceTransformationException(String.format("Minification failed for the following files:\n\t%s", StringUtils.join(visitor.failedFiles, "\n\t")), null);
        }
    }

    /**
     * Minifies each file in the source set
     */
    class MinifyFileVisitor implements FileVisitor {
        Object classpath;
        Boolean hasFailures = false;
        List<String> failedFiles = Lists.newArrayList();

        public MinifyFileVisitor(Object classpath) {
            this.classpath = classpath;
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            new File(destinationDir, dirDetails.getRelativePath().getPathString()).mkdirs();
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            File outputFileDir = new File(destinationDir, fileDetails.getRelativePath().getParent().getPathString());
            File outputFile = new File(outputFileDir, getMinifiedFileName(fileDetails.getName()));
            JavaExecAction action = new DefaultJavaExecAction(getFileResolver());
            action.setMain("com.google.javascript.jscomp.CommandLineRunner");
            action.args("--js", fileDetails.getFile().getPath(), "--js_output_file", outputFile.getPath());
            action.classpath(classpath);
            try {
                action.execute();
            } catch(ExecException e) {
                hasFailures = true;
                failedFiles.add(fileDetails.getRelativePath().getPathString());
            }
        }

        private String getMinifiedFileName(String fileName) {
            int extIndex = fileName.lastIndexOf('.');
            String prefix = fileName.substring(0, extIndex);
            String extension = fileName.substring(extIndex);
            return String.format("%s.min%s", prefix, extension);
        }
    }
}
