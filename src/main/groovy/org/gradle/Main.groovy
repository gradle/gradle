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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.project.BuildScriptFinder
import org.gradle.api.internal.project.EmbeddedBuildScriptFinder
import org.gradle.initialization.BuildSourceBuilder
import org.gradle.initialization.EmbeddedBuildExecuter
import org.gradle.util.GradleVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
// todo the main method is too long. Extract methods.
class Main {
    private static Logger logger = LoggerFactory.getLogger(Main)

    static final String GRADLE_HOME = 'gradle.home'
    static final String DEFAULT_GRADLE_USER_HOME = System.properties['user.home'] + '/.gradle'
    final static String DEFAULT_CONF_FILE = "conf.buildg"
    final static String DEFAULT_PLUGIN_PROPERTIES = "plugin.properties"
    final static String IMPORTS_FILE_NAME = "gradle-imports"
    final static String NL = System.properties['line.separator']

    static void main(String[] args) {
        long buildStartTime = System.currentTimeMillis()

        String toolsMainInfo = args[0]

        String gradleHome = System.properties[GRADLE_HOME]
        String embeddedBuildScript = null
        StartParameter startParameter = new StartParameter()
        startParameter.recursive = true
        startParameter.searchUpwards = true
        startParameter.currentDir = new File(System.properties.'user.dir')
        startParameter.gradleUserHomeDir = new File(DEFAULT_GRADLE_USER_HOME)
        startParameter.buildFileName = Project.DEFAULT_PROJECT_FILE

        def cli = new CliBuilder(usage: 'buildg -hnp "task1, ..., taskN')
        cli.h(longOpt: 'help', 'usage information')
        cli.n(longOpt: 'nonRecursive', 'Do not execute primary tasks of childprojects.')
        cli.S(longOpt: 'noJvmTermination', 'Don\'t trigger a System.exit(0) for normal termination. Use for Gradle\'s internal testing.')
        cli.u(longOpt: 'noSearchUpwards', 'Don\'t search in parent folders for a gradlesettings file.')
        cli.p(longOpt: 'projectDir', 'Use this dir instead of the current dir as the project dir.', args: 1)
        cli.l(longOpt: 'pluginProperties', 'Use this file as the plugin properties file.', args: 1)
        cli.b(longOpt: 'buildfile', 'Use this build file name (also for subprojects)', args: 1)
        cli.t(longOpt: 'tasks', 'Show list of tasks.')
        cli.d(longOpt: 'debug', 'Log in debug mode (includes normal stacktrace)')
        cli.i(longOpt: 'depInfo', 'Log dependency management output in quiet mode (Default mode is info).')
        cli.j(longOpt: 'depDebug', 'Log dependency management output in debug mode (Default mode is info).')
        cli.q(longOpt: 'quiet', 'Log erros only.')
        cli.f(longOpt: 'fullStacktrace', 'Print out the full (very verbose) stacktrace.')
        cli.s(longOpt: 'stacktrace', 'Print out the stacktrace.')
        cli.D(longOpt: 'prop', 'Set system property of the JVM (e.g. -Dmyprop=myvalue).', args: 1)
        cli.I(longOpt: 'noImports', 'Disable usage of default imports for build script files.')
        cli.P(longOpt: 'projectProperty', 'Set project property of the root project (e.g. -Pmyprop=myvalue).', args: 1)
        cli.g(longOpt: 'gradleUserHome', 'The user gradle home dir.', args: 1)
        cli.e(longOpt: 'embedded', 'Use an embedded build script.', args: 1)
        cli.v(longOpt: 'version', 'Prints version info.')

        def options = cli.parse(args.length < 2 ? [] as String[] : args[1..args.length - 1])

        if (!options) {
            println 'Illegal usage!'
            cli.usage
            return
        }

        configureLogger(options)

        logger.info(toolsMainInfo)

        if (options.h) {cli.usage()}

        if (options.v) {
            println(new GradleVersion().prettyPrint())
            System.exit(0)
        }

        if (!gradleHome) {
            logger.error("The gradle.home property is not set. Please set it and try again.")
            return
        }

        File pluginProperties = gradleHome + '/' + DEFAULT_PLUGIN_PROPERTIES as File
        File gradleImportsFile = gradleHome + '/' + IMPORTS_FILE_NAME as File

        if (options.I) {
            logger.info("Disabling default imports.")
            gradleImportsFile = null
        }

        if (options.D) {
            logger.info("Running with System props: $options.Ds")
            options.Ds.each {String keyValueExpression ->
                List elements = keyValueExpression.split('=')
                startParameter.systemPropertiesArgs[elements[0]] = elements.size() == 1 ? '' : elements[1]
            }
        }

        if (options.P) {
            logger.info("Running with Project props: $options.Ps")
            options.Ps.each {String keyValueExpression ->
                List elements = keyValueExpression.split('=')
                startParameter.projectProperties[elements[0]] = elements.size() == 1 ? '' : elements[1]
            }
        }

        if (options.n) startParameter.recursive = false
        if (options.u) {startParameter.searchUpwards = false}

        if (options.p) {
            startParameter.currentDir = new File(options.p)
            if (!startParameter.currentDir.isDirectory()) {
                logger.error("Error: Directory $startParameter.currentDir.canonicalFile does not exists!")
                return
            }
        }

        if (options.g) {
            startParameter.gradleUserHomeDir = new File(options.g)
        }

        if (options.b) {
            startParameter.buildFileName = options.b
        }

        if (options.l) {
            pluginProperties = options.l
        }

        if (options.e) {
            if (options.b || options.n || options.u) {
                logger.error("Error: The e option can't be used together with the b, n or u option.")
                return
            }
            embeddedBuildScript = options.e
        }

        logger.debug("gradle.home=$gradleHome")
        logger.debug("Current dir: $startParameter.currentDir")
        logger.debug("Gradle user home: $startParameter.gradleUserHomeDir")
        logger.info("Recursive: $startParameter.recursive")
        logger.info("Buildfilename: $startParameter.buildFileName")
        logger.debug("Plugin properties: $pluginProperties")
        logger.debug("Default imports file: $gradleImportsFile")

        try {
            startParameter.taskNames = options.arguments()
            if (!startParameter.taskNames && !options.t) {
                logger.error(NL + 'Build exits abnormally. No task names are specified!')
                return
            }

            def buildScriptFinder = (embeddedBuildScript != null ? new EmbeddedBuildScriptFinder(embeddedBuildScript) :
                new BuildScriptFinder(startParameter.buildFileName))
            Closure buildFactory = Build.newInstanceFactory(pluginProperties, gradleImportsFile)
            Build build = buildFactory(buildScriptFinder, null)
            build.settingsProcessor.buildSourceBuilder = new BuildSourceBuilder(
                    new EmbeddedBuildExecuter(buildFactory))

            if (options.t) {
                if (embeddedBuildScript != null) {
                    println(build.taskListNonRecursivelyWithCurrentDirAsRoot(startParameter))
                } else {
                    println(build.taskList(startParameter))
                }
                if (!options.S) {System.exit(0)}
                return
            }

            if (embeddedBuildScript != null) {
                build.runNonRecursivelyWithCurrentDirAsRoot(startParameter)
            } else {
                build.run(startParameter)
            }
            logger.info(NL + "BUILD SUCCESSFUL")
        } catch (BuildException e) {
            handleGradleException(e, options.s, options.d, options.f, buildStartTime)
        } catch (GradleException e) {
            handleGradleException(e, options.s, options.d, options.f, buildStartTime)
        } catch (Throwable e) {
            logger.error(NL + "Build aborted anormally because of an internal error. Run with -d option to get additonal debug info. Please file an issue at: www.gradle.org")
            logger.error("Exception is:", e)
            finalOutput(buildStartTime)
            stopExecutionWithError()
        }
        finalOutput(buildStartTime)
        if (!options.S) {System.exit(0)}
    }

    static void handleGradleException(Throwable t, boolean stacktrace, boolean debug, boolean fullStacktrace, long buildStartTime) {
        String introMessage = "Build aborted anormally. "
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
        stopExecutionWithError()
    }

    static void finalOutput(long buildStartTime) {
        logger.info(NL + "Total time: " + ((System.currentTimeMillis() - buildStartTime).intdiv(1000)) + ' seconds')
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
            stopExecutionWithError()
        } else if (options.i) {
            ivyLogLevel = Message.MSG_ERR
        } else if (options.j) {
            ivyLogLevel = Message.MSG_DEBUG
        } else {
            ivyLogLevel = Message.MSG_INFO
        }

        if (options.d) {
            throwExceptionIf(options, 'q', 'm')
            loglevel = 'DEBUG'
            layout = debugLayout
        } else if (options.q) {
            throwExceptionIf(options, 'm')
            loglevel = 'ERROR'
            layout = normalLayout
            if (!options.i && !options.j) {
                ivyLogLevel = Message.MSG_ERR
            }
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
                stopExecutionWithError()
            }
        }
    }

    private static stopExecutionWithError() {
        System.err.println("Exit with error!")
        System.exit(1)
    }

}