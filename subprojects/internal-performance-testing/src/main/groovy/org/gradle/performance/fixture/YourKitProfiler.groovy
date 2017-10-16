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
import org.gradle.internal.os.OperatingSystem
/**
 *
 * Helper class for adding Yourkit Java Profiler (YJP) agent start up arguments for builds launched in performance tests.
 * <p>
 * Currently, YJP_HOME environment variable or YJP_AGENT_PATH environment variable is used to locate the agent library file. This is not needed if you install YJP in /opt/yjp on Linux
 * or rename the application as "yjp" on macOS (directory: /Applications/yjp.app). These locations are searched by default.
 *
 * <p>
 * Examples of specifying YJP_HOME
 * <pre>
 * export YJP_HOME="/opt/yjp" # on linux
 * export YJP_HOME="/Applications/yjp.app/Contents/Resources" # on macOS
 * </pre>
 *
 * <p>
 * Examples of specifying YJP_AGENT_PATH directly
 * <pre>
 * export YJP_AGENT_PATH="/opt/yjp/bin/linux-x86-64/libyjpagent.so" # on linux
 * export YJP_AGENT_PATH="/Applications/yjp.app/Contents/Resources/bin/mac/libyjpagent.jnilib" # on macOS
 * </pre>
 *
 * <p>
 * If you profile on Windows, you will have to add YJP_AGENT_PATH environment variable pointing to the yjpagent.dll file (YJP installation directory\bin\win64\yjpagent.dll).
 *
 * <p>
 * Performance tests extending {@link org.gradle.performance.AbstractCrossBuildPerformanceTest} or {@link org.gradle.performance.AbstractCrossVersionPerformanceTest} will add
 * YJP agent parameters to the Gradle build process when you add {@code -Porg.gradle.performance.use_yourkit } parameter to the command line.  You can also enable the YourKit
 * profiler by adding -Dorg.gradle.performance.use_yourkit=1 to the VM options from IntelliJ.
 *
 * <p>
 * Example of running a single performance test with YJP agent options:
 * <pre>
 * ./gradlew performance:performanceTest -Porg.gradle.performance.use_yourkit -D:performance:performanceTest.single=NativePreCompiledHeaderPerformanceTest
 * </pre>
 *
 * <p>
 * By default, YJP will create a snapshot in ~/Snapshots when the profiled process terminates. The file name contains the test project name and display name from the performance test.
 *
 * <p>
 * This integration reads YourKit agent options from {@code ~/.gradle/yourkit.properties} file. <a href="https://www.yourkit.com/docs/java/help/startup_options.jsp">YourKit startup options</a>
 *
 * <p>
 * Example settings for tracing profiling
 * <pre>
 * tracing=true
 * disablealloc=true
 * monitors=true
 * probe_disable=*
 * delay=0
 * </pre>
 * <p>
 * Tweak tracing settings in {@code ~/.yjp/tracing.txt}, <a href="http://www.yourkit.com/docs/java/help/tracing_settings.jsp">tracing.txt reference</a>
 * useful setting is "adaptive=false" to trace all method calls.
 * <p>
 * Example settings for sampling profiling
 * <pre>
 * sampling=true
 * disablealloc=true
 * monitors=true
 * probe_disable=*
 * delay=0
 * </pre>
 * <p>
 * Tweak sampling settings in {@code ~/.yjp/sampling.txt}, <a href="http://www.yourkit.com/docs/java/help/sampling_settings.jsp">sampling.txt reference</a>
 * useful setting is "sampling_period_ms", which is 20 ms by default.
 *
 * Example settings for call counting profiling
 * <pre>
 * call_counting=true
 * disablealloc=true
 * monitors=true
 * probe_disable=*
 * delay=0
 * </pre>
 * <p>
 *
 * Defaults are currently:
 * <pre>
 * sampling=true
 * disablealloc=true
 * monitors=true
 * probe_disable=*
 * delay=0
 * </pre>
 */
@CompileStatic
class YourKitProfiler implements Profiler {
    private static final String USE_YOURKIT = "org.gradle.performance.use_yourkit"
    private static final Set<String> NO_ARGS_OPTIONS =
        ['onlylocal', 'united_log', 'sampling', 'tracing', 'call_counting', 'allocsampled', 'monitors', 'disablestacktelemetry', 'disableexceptiontelemetry', 'disableoomedumper', 'disablealloc', 'disabletracing', 'disableall'] as Set
    private static final File DEFAULT_YOURKIT_PROPERTIES_FILE = new File(System.getProperty("user.home"), ".gradle/yourkit.properties")
    private String yjpAgentPath
    private String yjpHome
    private OperatingSystem operatingSystem

    public YourKitProfiler() {
        this(System.getenv("YJP_AGENT_PATH"), System.getenv("YJP_HOME"), OperatingSystem.current())
    }

    public YourKitProfiler(String yjpAgentPath, String yjpHome, OperatingSystem operatingSystem) {
        this.yjpAgentPath = yjpAgentPath
        this.yjpHome = yjpHome
        this.operatingSystem = operatingSystem
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

    @Override
    void addProfilerDefaults(GradleInvocationSpec.InvocationBuilder invocation) {
        if (System.getProperty(USE_YOURKIT)) {
            invocation.useProfiler().profilerOpts(loadProperties())
        }
    }

    @Override
    List<String> profilerArguments(Map<String, Object> yourkitOptions) {
        String resolvedYjpAgentPath = locateYjpAgent()
        String yjpAgentOptions = buildYjpAgentOptionsString(yourkitOptions)
        return [ "-agentpath:$resolvedYjpAgentPath=$yjpAgentOptions".toString() ]
    }

    public Map<String, Object> loadProperties() {
        loadProperties(DEFAULT_YOURKIT_PROPERTIES_FILE)
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
            throw new IllegalArgumentException("You must specify some YourKit agent options.")
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
            throw new IllegalStateException("Cannot find YourKit agent library ($resolvedYjpAgentPath). Add YJP_AGENT_PATH environment variable that points to Yourkit JVM agent library or add YJP_HOME environment variable that points to Yourkit installation directory.")
        }
        resolvedYjpAgentPath
    }
}
