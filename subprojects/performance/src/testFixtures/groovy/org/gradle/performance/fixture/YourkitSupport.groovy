/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.os.OperatingSystem

/**
 *
 * Helper class for adding Yourkit Java Profiler (YJP) agent start up arguments for builds launched in performance tests.
 *
 * For adding the arguments, the Yourkit installation directory should be known by this implemantation. Currently YJP_HOME environment variable or YJP_AGENT_PATH environment variable is used to locate the agent library file.
 * This is not needed if you install YJP in /opt/yjp on Linux or rename the application as "yjp" on Mac OS X (directory: /Applications/yjp.app). These locations are searched by default.
 *
 * Examples of specifying YJP_HOME
 * <pre>
 * {@code
 * export YJP_HOME=/opt/yjp # on linux
 * export YJP_HOME=/Applications/yjp.app/Contents/Resources # on Mac OS X
 *}
 * </pre>
 *
 * Examples of specifying YJP_AGENT_PATH directly
 * <pre>
 * {@code
 * export YJP_AGENT_PATH=/opt/yjp/bin/linux-x86-64/libyjpagent.so # on linux
 * export YJP_AGENT_PATH=/Applications/yjp.app/Contents/Resources/bin/mac/libyjpagent.jnilib # on Mac OS X
 *}
 * </pre>
 *
 * If you profile on Windows, you will have to add YJP_AGENT_PATH environment variable pointing to the yjpagent.dll file (YJP installation directory\bin\win64\yjpagent.dll).
 *
 * Performance tests extending AbstractCrossBuildPerformanceTest or AbstractCrossVersionPerformanceTest will add YJP agent parameters to the Gradle build process when you add {@code -Porg.gradle.performance.use_yourkit } parameter to the command line.
 *
 * Example of running a single performance test with YJP agent options:
 * <pre>
 * {@code
 * ./gradlew performance:performanceTest -Porg.gradle.performance.use_yourkit -D:performance:performanceTest.single=NativePreCompiledHeaderPerformanceTest
 *}
 * </pre>
 *
 * By default, YJP will create a snapshot in ~/Snapshots when the profiled process terminates. The file name contains the test project name and display name from the performance test.
 *
 * This integration reads Yourkit agent options from {@code ~/.gradle/yourkit.properties } file. <a href="https://www.yourkit.com/docs/java/help/startup_options.jsp">Yourkit startup options</a>
 *
 * example settings for tracing profiling
 * <pre>
 * {@code
 * tracing=true
 * disablealloc=true
 * monitors=true
 * probe_disable=*
 * delay=0
 *}
 * </pre>
 * tweak tracing settings in {@code ~/.yjp/tracing.txt } , <a href="http://www.yourkit.com/docs/java/help/tracing_settings.jsp">tracing.txt reference</a>
 * useful setting is "adaptive=false" to trace all method calls.
 *
 * example settings for sampling profiling
 * <pre>
 * {@code
 * sampling=true
 * disablealloc=true
 * monitors=true
 * probe_disable=*
 * delay=0
 *}
 * </pre>
 * tweak sampling settings in {@code ~/.yjp/sampling.txt } , <a href="http://www.yourkit.com/docs/java/help/sampling_settings.jsp">sampling.txt reference</a>
 * useful setting is "sampling_period_ms", which is 20 ms by default.
 *
 * example settings for call counting profiling
 * <pre>
 * {@code
 * call_counting=true
 * disablealloc=true
 * monitors=true
 * probe_disable=*
 * delay=0
 *}
 * </pre>
 *
 */
@CompileStatic
class YourkitSupport {
    static final String USE_YOURKIT = "org.gradle.performance.use_yourkit"
    private static final Set<String> NO_ARGS_OPTIONS =
        ['onlylocal', 'united_log', 'sampling', 'tracing', 'call_counting', 'allocsampled', 'monitors', 'disablestacktelemetry', 'disableexceptiontelemetry', 'disableoomedumper', 'disablealloc', 'disabletracing', 'disableall'] as Set
    private static final File DEFAULT_YOURKIT_PROPERTIES_FILE = new File(System.getProperty("user.home"), ".gradle/yourkit.properties")
    private String yjpAgentPath
    private String yjpHome
    private OperatingSystem operatingSystem

    public YourkitSupport() {
        this(System.getenv("YJP_AGENT_PATH"), System.getenv("YJP_HOME"), OperatingSystem.current())
    }

    public YourkitSupport(String yjpAgentPath, String yjpHome, OperatingSystem operatingSystem) {
        this.yjpAgentPath = yjpAgentPath
        this.yjpHome = yjpHome
        this.operatingSystem = operatingSystem
    }

    public static Map<String, Object> loadProperties() {
        loadProperties(DEFAULT_YOURKIT_PROPERTIES_FILE)
    }

    @CompileDynamic
    public static Map<String, Object> loadProperties(File yourkitPropertiesFile) {
        Map<String, Object> yourkitOptions
        if (yourkitPropertiesFile.exists()) {
            Properties yourkitProperties = new Properties()
            yourkitPropertiesFile.withInputStream {
                yourkitProperties.load(it)
            }
            yourkitOptions = [:] + yourkitProperties
        } else {
            yourkitOptions = [sampling     : true,
                              disablealloc : true,
                              monitors     : true,
                              probe_disable: '*']
        }
        yourkitOptions
    }

    static void handleBuildInvocation(GradleInvocationSpec.Builder invocation) {
        if (System.getProperty(USE_YOURKIT)) {
            invocation.useYourkit().yourkitOpts(YourkitSupport.loadProperties())
        }
    }

    void enableYourkit(GradleExecuter executer, Map<String, Object> yourkitOptions) {
        String resolvedYjpAgentPath = locateYjpAgent()
        String yjpAgentOptions = buildYjpAgentOptionsString(yourkitOptions)
        String jvmOption = "-agentpath:$resolvedYjpAgentPath=$yjpAgentOptions".toString()
        executer.withBuildJvmOpts(jvmOption)
    }

    private String buildYjpAgentOptionsString(Map<String, Object> yourkitOptions) {
        def yjpOptionsStringBuilder = new StringBuilder()
        if (yourkitOptions) {
            yourkitOptions.each { k, v ->
                String optionName = k.toString()
                String optionValue = v?.toString()
                if (yjpOptionsStringBuilder.length() > 0) {
                    yjpOptionsStringBuilder.append(',')
                }
                yjpOptionsStringBuilder.append(optionName)
                if (!NO_ARGS_OPTIONS.contains(optionName)) {
                    yjpOptionsStringBuilder.append('=')
                    yjpOptionsStringBuilder.append(optionValue)
                }
            }
        }
        if (yjpOptionsStringBuilder.length() == 0) {
            throw new IllegalArgumentException("You must specify some Yourkit agent options.")
        }
        yjpOptionsStringBuilder.toString()
    }

    private String locateYjpAgent() {
        String resolvedYjpAgentPath = yjpAgentPath
        if (!resolvedYjpAgentPath) {
            def resolvedYjpHome = yjpHome
            if (!resolvedYjpHome) {
                if (operatingSystem.isMacOsX()) {
                    resolvedYjpHome = "/Applications/yjp.app/Contents/Resources"
                } else {
                    resolvedYjpHome = "/opt/yjp"
                }
            }
            if (operatingSystem.isMacOsX()) {
                resolvedYjpAgentPath = "$resolvedYjpHome/bin/mac/libyjpagent.jnilib"
            } else {
                resolvedYjpAgentPath = "$resolvedYjpHome/bin/linux-x86-64/libyjpagent.so"
            }
        }
        if (!resolvedYjpAgentPath || !new File(resolvedYjpAgentPath).isFile()) {
            throw new IllegalStateException("Cannot find Yourkit agent library ($resolvedYjpAgentPath). Add YJP_AGENT_PATH environment variable that points to Yourkit JVM agent library or add YJP_HOME environment variable that points to Yourkit installation directory.")
        }
        resolvedYjpAgentPath
    }
}
