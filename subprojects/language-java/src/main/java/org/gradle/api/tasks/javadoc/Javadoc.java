/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks.javadoc;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.javadoc.internal.JavadocSpec;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>Generates HTML API documentation for Java classes.</p>
 * <p>
 * If you create your own Javadoc tasks remember to specify the 'source' property!
 * Without source the Javadoc task will not create any documentation. Example:
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * task myJavadocs(type: Javadoc) {
 *   source = sourceSets.main.allJava
 * }
 * </pre>
 *
 * <p>
 * An example how to create a task that runs a custom doclet implementation:
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * configurations {
 *   jaxDoclet
 * }
 *
 * dependencies {
 *   //jaxDoclet "some.interesting:Dependency:1.0"
 * }
 *
 * task generateRestApiDocs(type: Javadoc) {
 *   source = sourceSets.main.allJava
 *   destinationDir = reporting.file("rest-api-docs")
 *   options.docletpath = configurations.jaxDoclet.files.asType(List)
 *   options.doclet = "com.lunatech.doclets.jax.jaxrs.JAXRSDoclet"
 *   options.addStringOption("jaxrscontext", "http://localhost:8080/myapp")
 * }
 * </pre>
 */
@CacheableTask
@ParallelizableTask
public class Javadoc extends SourceTask {
    private File destinationDir;

    private boolean failOnError = true;

    private String title;

    private String maxMemory;

    private StandardJavadocDocletOptions options = new StandardJavadocDocletOptions();

    private FileCollection classpath = getProject().files();

    private String executable;

    @TaskAction
    protected void generate() {
        final File destinationDir = getDestinationDir();

        StandardJavadocDocletOptions options = new StandardJavadocDocletOptions((StandardJavadocDocletOptions) getOptions());

        if (options.getDestinationDirectory() == null) {
            options.destinationDirectory(destinationDir);
        }

        options.classpath(new ArrayList<File>(getClasspath().getFiles()));

        if (!GUtil.isTrue(options.getWindowTitle()) && GUtil.isTrue(getTitle())) {
            options.windowTitle(getTitle());
        }
        if (!GUtil.isTrue(options.getDocTitle()) && GUtil.isTrue(getTitle())) {
            options.setDocTitle(getTitle());
        }

        String maxMemory = getMaxMemory();
        if (maxMemory != null) {
            final List<String> jFlags = options.getJFlags();
            final Iterator<String> jFlagsIt = jFlags.iterator();
            boolean containsXmx = false;
            while (!containsXmx && jFlagsIt.hasNext()) {
                final String jFlag = jFlagsIt.next();
                if (jFlag.startsWith("-Xmx")) {
                    containsXmx = true;
                }
            }
            if (!containsXmx) {
                options.jFlags("-Xmx" + maxMemory);
            }
        }

        List<String> sourceNames = new ArrayList<String>();
        for (File sourceFile : getSource()) {
            sourceNames.add(sourceFile.getAbsolutePath());
        }
        options.setSourceNames(sourceNames);

        executeExternalJavadoc(options);
    }

    private void executeExternalJavadoc(StandardJavadocDocletOptions options) {
        JavadocSpec spec = new JavadocSpec();
        spec.setExecutable(getExecutable());
        spec.setOptions(options);
        spec.setIgnoreFailures(!isFailOnError());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setOptionsFile(getOptionsFile());

        Compiler<JavadocSpec> generator = ((JavaToolChainInternal) getToolChain()).select(getPlatform()).newCompiler(JavadocSpec.class);
        generator.execute(spec);
    }

    /**
     * {@inheritDoc}
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * Returns the tool chain that will be used to generate the Javadoc.
     */
    @Incubating @Inject
    public JavaToolChain getToolChain() {
        // Implementation is generated
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the tool chain to use to generate the Javadoc.
     */
    @Incubating
    public void setToolChain(JavaToolChain toolChain) {
        // Implementation is generated
        throw new UnsupportedOperationException();
    }

    @Internal
    private JavaPlatform getPlatform() {
        return DefaultJavaPlatform.current();
    }

    /**
     * <p>Returns the directory to generate the documentation into.</p>
     *
     * @return The directory.
     */
    @Internal
    public File getDestinationDir() {
        return destinationDir;
    }

    @OutputDirectory
    protected File getOutputDirectory() {
        File destinationDir = getDestinationDir();
        if (destinationDir == null) {
            destinationDir = options.getDestinationDirectory();
        }
        return destinationDir;
    }

    /**
     * <p>Sets the directory to generate the documentation into.</p>
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * Returns the amount of memory allocated to this task.
     */
    @Internal
    public String getMaxMemory() {
        return maxMemory;
    }

    /**
     * Sets the amount of memory allocated to this task.
     *
     * @param maxMemory The amount of memory
     */
    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
    }

    /**
     * <p>Returns the title for the generated documentation.</p>
     *
     * @return The title, possibly null.
     */
    @Input
    @Optional
    public String getTitle() {
        return title;
    }

    /**
     * <p>Sets the title for the generated documentation.</p>
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns whether Javadoc generation is accompanied by verbose output.
     *
     * @see #setVerbose(boolean)
     */
    @Internal
    public boolean isVerbose() {
        return options.isVerbose();
    }

    /**
     * Sets whether Javadoc generation is accompanied by verbose output or not. The verbose output is done via println
     * (by the underlying Ant task). Thus it is not handled by our logging.
     *
     * @param verbose Whether the output should be verbose.
     */
    public void setVerbose(boolean verbose) {
        if (verbose) {
            options.verbose();
        }
    }

    /**
     * Returns the classpath to use to resolve type references in the source code.
     *
     * @return The classpath.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath to use to resolve type references in this source code.
     *
     * @param classpath The classpath. Must not be null.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns the Javadoc generation options.
     *
     * @return The options. Never returns null.
     */
    @Nested
    public MinimalJavadocOptions getOptions() {
        return options;
    }

    /**
     * Sets the Javadoc generation options.
     *
     * @param options The options. Must not be null.
     */
    @Deprecated
    public void setOptions(MinimalJavadocOptions options) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Javadoc.setOptions(MinimalJavadocOptions)");
        if (options instanceof StandardJavadocDocletOptions) {
            this.options = (StandardJavadocDocletOptions) options;
        } else {
            this.options = new StandardJavadocDocletOptions(options);
        }
    }

    /**
     * Convenience method for configuring Javadoc generation options.
     *
     * @param block The configuration block for Javadoc generation options.
     */
    public void options(Closure<?> block) {
        getProject().configure(getOptions(), block);
    }

    /**
     * Specifies whether this task should fail when errors are encountered during Javadoc generation. When {@code true},
     * this task will fail on Javadoc error. When {@code false}, this task will ignore Javadoc errors.
     */
    @Input
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @Internal
    public File getOptionsFile() {
        return new File(getTemporaryDir(), "javadoc.options");
    }

    /**
     * Returns the Javadoc executable to use to generate the Javadoc. When {@code null}, the Javadoc executable for
     * the current JVM is used.
     *
     * @return The executable. May be null.
     */
    @Input @Optional
    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }
}
