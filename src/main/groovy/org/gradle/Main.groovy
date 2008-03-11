/*
 * Copyright 2007 the original author or authors.
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
package org.gradle

import ch.qos.logback.classic.Level
import org.apache.ivy.util.DefaultMessageLogger
import org.apache.ivy.util.Message
import org.apache.tools.ant.BuildException
import org.codehaus.groovy.runtime.StackTraceUtils
import org.gradle.api.DependencyManager
import org.gradle.api.GradleException
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.configuration.BuildClasspathLoader
import org.gradle.configuration.BuildConfigurer
import org.gradle.configuration.ProjectDependencies2TasksResolver
import org.gradle.configuration.ProjectTasksPrettyPrinter
import org.gradle.execution.BuildExecuter
import org.gradle.execution.Dag
import org.gradle.initialization.ProjectsLoader
import org.gradle.initialization.SettingsFileHandler
import org.gradle.initialization.SettingsProcessor
import org.gradle.util.GradleVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class Main {
    static final String GRADLE_HOME = 'gradle.home'
    static final String DEFAULT_GRADLE_USER_HOME = System.properties['user.home'] + '/.gradle' 
    static Logger logger = LoggerFactory.getLogger(Main)
    final static String DEFAULT_CONF_FILE = "conf.buildg"
    final static String DEFAULT_PLUGIN_PROPERTIES = "plugin.properties"
    final static String NL = System.properties['line.separator']

    static void main(String[] args) {
        long buildStartTime = System.currentTimeMillis()
        boolean recursive = true
        boolean searchUpwards = true
        File currentDir = new File(System.properties.'user.dir')
        File gradleUserHomeDir = new File(DEFAULT_GRADLE_USER_HOME)
        String buildFileName = BuildScriptProcessor.DEFAULT_PROJECT_FILE
        String gradleHome = System.properties[GRADLE_HOME]

        def cli = new CliBuilder(usage: 'buildg -hnp "task1, ..., taskN')
        cli.h(longOpt: 'help', 'usage information')
        cli.n(longOpt: 'nonRecursive', 'Don\'t execute the tasks for the cildprojects of the current project')
        cli.u(longOpt: 'noSearchUpwards', 'Don\'t search in parent folders for gradlesettings file.')
        cli.p(longOpt: 'projectDir', 'Use this dir instead of the current dir as the project dir.', args: 1)
        cli.l(longOpt: 'pluginDirsPath', 'Colon separated string of the plugin dirs to scan for plugins.', args: 1)
        cli.b(longOpt: 'buildfile', 'Use this build file name (also for subprojects)', args: 1)
        cli.t(longOpt: 'tasks', 'Show list of tasks.')
        cli.d(longOpt: 'debug', 'Log in debug mode (includes normal stacktrace)')
        cli.i(longOpt: 'depInfo', 'info output from dependency management')
        cli.j(longOpt: 'depDebug', 'debug output from dependency management')
        cli.q(longOpt: 'quiet', 'Log in quiet mode.')
        cli.f(longOpt: 'fullStacktrace', 'Print out the full (very verbose) stacktrace.')
        cli.s(longOpt: 'stacktrace', 'Print out the stacktrace.')
        cli.D(longOpt: 'prop', 'Set system property of the JVM.', args: 1)
        cli.g(longOpt: 'gradleUserHome', 'The user specific gradle dir.', args: 1)
        cli.v(longOpt: 'version', 'Prints put version info.')

        def options = cli.parse(args)

        if (!options) {
            println 'Illegal usage!'
            cli.usage
            return
        }

        configureLogger(options)

        if (options.h) {cli.usage()}

        if (options.v) {
            println(new GradleVersion().prettyPrint())
            System.exit(0)
        }

        if (!gradleHome) {
            logger.error("The gradle.home property is not set. Please set it and try again.")
            return
        }

        if (options.D) {
            logger.info("Running with System props: $options.Ds")
            options.Ds.each {String keyValueExpression ->
                List elements = keyValueExpression.split('=')
                System.properties[elements[0]] = elements.size() == 1 ? '' : elements[1]
            }
        }

        File pluginProperties = gradleHome + '/' + DEFAULT_PLUGIN_PROPERTIES as File

        if (options.n) recursive = false
        if (options.u) {searchUpwards = false}

        if (options.p) {
            currentDir = new File(options.p)
            if (!currentDir.isDirectory()) {
                logger.error("Error: Directory $currentDir.canonicalFile does not exists!")
                return
            }
        }

        if (options.g) {
            gradleUserHomeDir = new File(options.g)
        }

        if (options.b) {
            buildFileName = options.b
        }

        if (options.l) {
            pluginProperties = options.l
        }

        logger.info("gradle.home=$gradleHome")
        logger.info("Current dir: $currentDir")
        logger.info("Gradle user home: $gradleUserHomeDir")
        logger.info("Recursive: $recursive")
        logger.info("Buildfilename: $buildFileName")
        logger.info("Plugin properties: $pluginProperties")

        try {
            Build build = new Build(new SettingsProcessor(new SettingsFileHandler(),
                    createDependencyManagerForBuildClasspath(gradleUserHomeDir)),
                    new ProjectsLoader(new ProjectFactory(new DefaultDependencyManagerFactory()), new BuildScriptProcessor(), new BuildScriptFinder(buildFileName), new PluginRegistry(pluginProperties)),
                    new BuildConfigurer(new ProjectDependencies2TasksResolver(), new BuildClasspathLoader(), new ProjectsTraverser(), new ProjectTasksPrettyPrinter()),
                    new BuildExecuter(new Dag()))

            if (options.t) {
                println(build.taskList(currentDir, gradleUserHomeDir, buildFileName, recursive, searchUpwards))
                return
            }

            def tasks = options.arguments()
            if (!tasks) {
                logger.error(NL + 'Build exits abnormally. No task names are specified!')
                return
            }
            build.run(tasks, currentDir, gradleUserHomeDir, buildFileName, recursive, searchUpwards)
            logger.info(NL + "BUILD SUCCESSFUL")
        } catch (BuildException e) {
            handleGradleException(e, options.s, options.d, options.f, buildStartTime)
        } catch (GradleException e) {
            handleGradleException(e, options.s, options.d, options.f, buildStartTime)
        } catch (Throwable e) {
            logger.error(NL + "Build aborted anormally because of an internal error. Run with -d option to get additonal debug info. Please file an issue at: www.gradle.org")
            logger.error("Exception is:", e)
            finalOutput(buildStartTime)
            System.exit(1)
        } finally {
            finalOutput(buildStartTime)
        }
    }

    static void handleGradleException(Throwable t, boolean stacktrace, boolean debug, boolean fullStacktrace, long buildStartTime) {
        String introMessage = "Build aborted anormally because of invalid user data. Please check your scripts!"
        introMessage += (stacktrace || debug) ? '' : " Run with -s option to get stacktrace."
        introMessage += debug ? '' : " Run with -d option to get all debug info including stacktrace."
        introMessage += fullStacktrace ? '' : " Run (additionally) with -f option to get the full (very verbose) stacktrace"
        logger.error(NL + introMessage)
        if (debug || stacktrace || fullStacktrace) {
           logger.error("Exception is:", fullStacktrace ? t : StackTraceUtils.deepSanitize(t))
        } else {
           logger.error("Exception: $t")
        }
        finalOutput(buildStartTime)
        System.exit(1)
    }

    static void finalOutput(long buildStartTime) {
        logger.info(NL + "Total time: " + ((System.currentTimeMillis() - buildStartTime).intdiv(1000)) + ' seconds')
    }

    static private DependencyManager createDependencyManagerForBuildClasspath(File gradleUserHomeDir) {
        DependencyManager dependencyManager = new DefaultDependencyManagerFactory().createDependencyManager()
        DefaultProject dummyProjectForDepencencyManager = new DefaultProject()
        dummyProjectForDepencencyManager.group = 'org.gradle'
        dummyProjectForDepencencyManager.name = 'build'
        dummyProjectForDepencencyManager.version = 'na'
        dummyProjectForDepencencyManager.status = 'release'
        dummyProjectForDepencencyManager.gradleUserHome = gradleUserHomeDir.canonicalPath
        dependencyManager.project = dummyProjectForDepencencyManager
        dependencyManager
    }

    static void configureLogger(def options) {
        //String normalLayout = '%msg%n'
        String normalLayout = '%msg%n'
        String debugLayout = '%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n'

        ch.qos.logback.classic.Logger rootLogger = LoggerFactory.getLogger('root')

        String loglevel = null
        String layout = null

        int ivyLogLevel
        if (options.i && options.j) {
            println("Error: For the dependency output you must either set 'i' or 'j'. Not Both!")
            System.exit(1)
        } else if (options.i) {
            ivyLogLevel = Message.MSG_INFO
        } else if (options.j) {
            ivyLogLevel = Message.MSG_DEBUG
        } else {
            ivyLogLevel = Message.MSG_ERR
        }

        if (options.d) {
            throwExceptionIf(options, 'q', 'm')
            loglevel = 'DEBUG'
            layout = debugLayout
        } else if (options.q) {
            throwExceptionIf(options, 'm')
            loglevel = 'ERROR'
            layout = normalLayout
        } else if (options.m) {
            loglevel = 'INFO'
            layout = normalLayout
        } else {
            loglevel = 'INFO'
            layout = normalLayout
        }
        Message.setDefaultLogger(new DefaultMessageLogger(ivyLogLevel))
        rootLogger.setLevel(Level.toLevel(loglevel))

    }

    private static String throwExceptionIf(def options, String[] hasFlags) {
        hasFlags.each {
            if (options."$it") {
                println("Error: Loglevel is already set. Can't specify it again to: $it")
                System.exit(1)
            }
        }
    }

}