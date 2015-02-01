/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins

import org.apache.commons.lang.StringUtils
import org.apache.tools.ant.filters.BaseFilterReader
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.util.DeprecationLogger
import org.gradle.util.TextUtil

/**
 * <p>A {@link Plugin} which runs a project as a Java Application.</p>
 *
 */
class ApplicationPlugin implements Plugin<Project> {
    static final String APPLICATION_PLUGIN_NAME = "application"
    static final String APPLICATION_GROUP = APPLICATION_PLUGIN_NAME

    static final String TASK_RUN_NAME = "run"
    static final String TASK_START_SCRIPTS_NAME = "startScripts"
    static final String TASK_INSTALL_NAME = "installApp"
    static final String TASK_DIST_ZIP_NAME = "distZip"
    static final String TASK_DIST_TAR_NAME = "distTar"

    /**
     * The name of the application.
     */
    public static final String DEFAULT_UNIX_TEMPLATE = 'unixStartScript.txt'
    public static final String DEFAULT_WINDOWS_TEMPLATE = 'windowsStartScript.txt'

    private Project project
    private ApplicationPluginConvention pluginConvention

    void apply(final Project project) {
        this.project = project
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(DistributionPlugin)

        addPluginConvention()
        addRunTask()
        addCreateScriptsTask()

        def distribution = project.distributions[DistributionPlugin.MAIN_DISTRIBUTION_NAME]
        distribution.conventionMapping.baseName = {pluginConvention.applicationName}
        configureDistSpec(distribution.contents)
        Task installAppTask = addInstallAppTask(distribution)
        configureInstallTasks(installAppTask, project.tasks[DistributionPlugin.TASK_INSTALL_NAME])
    }

    void configureInstallTasks(Task... installTasks) {
        installTasks.each { installTask ->
            installTask.doFirst {
                String resolvedBinDir = pluginConvention.applicationBinDir
                String resolvedLibDir = pluginConvention.applicationLibDir
                if (destinationDir.directory) {
                    if (!new File(destinationDir, resolvedLibDir).directory || !new File(destinationDir, resolvedBinDir).directory) {
                        throw new StopExecutionException("The specified installation directory '${destinationDir}' is neither empty nor does it contain an installation for '${pluginConvention.applicationName}'.\n" +
                                "If you really want to install to this directory, delete it and run the install task again.\n" +
                                "Alternatively, choose a different installation directory."
                        )
                    }
                }
            }
            installTask.doLast {
                String resolvedBinDir = pluginConvention.applicationBinDir
                project.ant.chmod(file: "${destinationDir.absolutePath}/${StringUtils.isBlank(resolvedBinDir) || ".".equals(resolvedBinDir) ? '' : resolvedBinDir + '/'}${pluginConvention.applicationName}", perm: 'ugo+x')
            }
        }
    }

    private void addPluginConvention() {
        pluginConvention = new ApplicationPluginConvention(project)
        pluginConvention.applicationName = project.name
        project.convention.plugins.application = pluginConvention
    }

    private void addRunTask() {
        def run = project.tasks.create(TASK_RUN_NAME, JavaExec)
        run.description = "Runs this project as a JVM application"
        run.group = APPLICATION_GROUP
        run.classpath = project.sourceSets.main.runtimeClasspath
        run.conventionMapping.main = { pluginConvention.mainClassName }
        run.conventionMapping.jvmArgs = { pluginConvention.applicationDefaultJvmArgs }
    }

    private void addCreateScriptsTask() {
        def startScripts = project.tasks.create(TASK_START_SCRIPTS_NAME, CreateStartScripts)
        startScripts.description = "Creates OS specific scripts to run the project as a JVM application."
        startScripts.classpath = project.tasks[JavaPlugin.JAR_TASK_NAME].outputs.files + project.configurations.runtime
        startScripts.conventionMapping.mainClassName = { pluginConvention.mainClassName }
        startScripts.conventionMapping.applicationName = { pluginConvention.applicationName }
        startScripts.conventionMapping.outputDir = { new File(project.buildDir, 'scripts') }
        startScripts.conventionMapping.defaultJvmOpts = { pluginConvention.applicationDefaultJvmArgs }

        startScripts.into({startScripts.getOutputDir()})
        startScripts.unixStartScripts = project.copySpec {
            from([StartScriptGenerator.getResource(DEFAULT_UNIX_TEMPLATE).getFile()] as Object[])
        }

        startScripts.unixStartScripts.filter([createStartScripts: startScripts] /*it's important to pass the task itself, to allow parameter customization; parameter name complies with setCreateStartScripts method of the filter class*/, LazyNixStartScriptGeneratorAdapter)
        startScripts.unixStartScripts.rename { filename ->
            DEFAULT_UNIX_TEMPLATE.equals(filename) ? pluginConvention.applicationName : filename
        }

        startScripts.with(startScripts.unixStartScripts)


        startScripts.windowsStartScripts = project.copySpec {
            from([StartScriptGenerator.getResource(DEFAULT_WINDOWS_TEMPLATE).getFile()] as Object[])
        }

        startScripts.windowsStartScripts.filter([createStartScripts: startScripts] /*it's important to pass the task itself, to allow parameter customization; parameter name complies with setCreateStartScripts method of the filter class*/, LazyWindowsStartScriptGeneratorAdapter)
        startScripts.windowsStartScripts.rename { filename ->
            DEFAULT_WINDOWS_TEMPLATE.equals(filename) ? "${pluginConvention.applicationName}.bat" : filename
        }

        startScripts.with(startScripts.windowsStartScripts)

    }

    private Task addInstallAppTask(Distribution distribution) {
        def installTask = project.tasks.create(TASK_INSTALL_NAME, Sync)
        installTask.description = "Installs the project as a JVM application along with libs and OS specific scripts."
        installTask.group = APPLICATION_GROUP
        installTask.with distribution.contents
        installTask.into { project.file("${project.buildDir}/install/${pluginConvention.applicationName}") }
        installTask.doFirst{
            DeprecationLogger.nagUserOfReplacedTask(ApplicationPlugin.TASK_INSTALL_NAME, DistributionPlugin.TASK_INSTALL_NAME);
        }
        installTask
    }

    private CopySpec configureDistSpec(CopySpec distSpec) {
        def jar = project.tasks[JavaPlugin.JAR_TASK_NAME]
        def startScripts = project.tasks[TASK_START_SCRIPTS_NAME]

        distSpec.with {
            from(project.file("src/dist"))

            into({
                String resolvedLibDir = pluginConvention.applicationLibDir
                StringUtils.isBlank(resolvedLibDir) || ".".equals(resolvedLibDir) ? '' : resolvedLibDir //enabling lib dir renaming and complete omitting
            }) {
                from(jar)
                from(project.configurations.runtime)
            }

            into({
                String resolvedBinDir = pluginConvention.applicationBinDir
                StringUtils.isBlank(resolvedBinDir) || ".".equals(resolvedBinDir) ? '' : resolvedBinDir //enabling bin dir renaming and complete omitting
            }) {
                from(startScripts)
                fileMode = 0755
            }
        }

        distSpec.with(pluginConvention.applicationDistribution)

        distSpec
    }

    static class LazyWindowsStartScriptGeneratorAdapter extends LazyNixStartScriptGeneratorAdapter {

        /**
         * Creates a new filtered reader.
         *
         * @param enclosing @param reader a Reader object providing the underlying stream.
         * @throws NullPointerException if <code>in</code> is <code>null</code>
         */
        public LazyWindowsStartScriptGeneratorAdapter(Reader reader) {
            super(reader)
            lineSeparator = TextUtil.windowsLineSeparator
        }

        @Override
        protected Map<String, String> makeTokens() {
            generator.getWindowsScriptBindings(quoteJvmOptsClosure != null ? generator.defaultJvmOpts.collect{ quoteJvmOptsClosure('win', it) } : generator.getWindowsQuotedJvmOpts())
        }
    }

    static class LazyNixStartScriptGeneratorAdapter extends BaseFilterReader {

        private static final int EOF = -1;

        private char[] buffer;
        private int index;
        protected final generator = new StartScriptGenerator()

        protected Map<String, String> tokens
        protected Closure<String> quoteJvmOptsClosure

        protected String lineSeparator

        /**
         * Creates a new filtered reader.
         *
         * @param reader a Reader object providing the underlying stream.
         * @throws NullPointerException if <code>in</code> is <code>null</code>
         */
        public LazyNixStartScriptGeneratorAdapter(Reader reader) {
            super(reader);
            lineSeparator = TextUtil.unixLineSeparator
        }

        public void setCreateStartScripts(CreateStartScripts startScripts) {
            def pluginCovnention = startScripts.project.convention.plugins.application
            def resolvedBinDir = pluginCovnention.applicationBinDir
            def resolvedLibDir = pluginCovnention.applicationLibDir
            generator.applicationName = startScripts.getApplicationName()
            generator.scriptRelPath = "${getRelPath(resolvedBinDir)}$generator.applicationName"
            generator.classpath = startScripts.classpath.getFiles().collect { it.name =~ /\.jar/ ? "${getRelPath(resolvedLibDir)}${it.name}" : "${it.name}" } //allowing non-jar classpath entries to be added as is (e.g. directories)
            generator.optsEnvironmentVar = startScripts.getOptsEnvironmentVar()
            generator.exitEnvironmentVar = startScripts.getExitEnvironmentVar()
            generator.mainClassName = startScripts.getMainClassName()
            generator.defaultJvmOpts = startScripts.getDefaultJvmOpts()
            if (startScripts.quoteJvmOptsClosure) {
                this.quoteJvmOptsClosure = startScripts.quoteJvmOptsClosure
            }

            this.tokens = startScripts.tokens
            if (tokens != null && !tokens.isEmpty()) {
                tokens.putAll(makeTokens()) //overriding in case someone chosen some token names we already use
            } else {
                this.tokens = makeTokens()
            }
        }

        String getRelPath(String aDir) {
            return !StringUtils.isBlank(aDir) && !aDir.equals(".") ? "$aDir/" : ""
        }


        protected Map<String, String> makeTokens() {
            generator.getUnixScriptBindings(quoteJvmOptsClosure != null ? /*passing into a given closure*/ generator.defaultJvmOpts.collect{ quoteJvmOptsClosure('nix', it) } : generator.getUnixQuotedJvmOpts())
        }

        /**
         * Returns the next character in the filtered stream. The original
         * stream is first read in fully, and the tokens are expanded via SimpleTemplateEngine
         * The results of this expansion are then queued so they can be read
         * character-by-character.
         *
         * @return the next character in the resulting stream, or -1
         * if the end of the resulting stream has been reached
         *
         * @exception IOException if the underlying stream throws an IOException
         * during reading
         */
        public int read() throws IOException {
            if (index > EOF) {
                if (buffer == null) {
                    String data = readFully();
                    try {
                        def nativeOutput = generator.generateNativeOutputFromText(data, tokens, lineSeparator)
                        buffer = nativeOutput == null ? new char[0]
                                : nativeOutput.toString().toCharArray();
                    } catch (MissingPropertyException mpe) {
                        throw new GradleException("Property ${mpe.property} is expected but not supplied for current template", mpe)
                    }
                }
                if (index < buffer.length) {
                    return buffer[index++];
                }
                index = EOF;
            }
            return EOF;
        }
    }
}