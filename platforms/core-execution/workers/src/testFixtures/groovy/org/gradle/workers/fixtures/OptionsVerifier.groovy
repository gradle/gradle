/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.fixtures

import groovy.json.JsonSlurper
import org.gradle.util.internal.TextUtil

class OptionsVerifier {
    private static final ARGUMENTS = "arguments"
    private static final BOOTSTRAP_CLASSPATH = "bootstrapClassPath"
    private static final ENV_VARIABLES = "environmentVariables"
    List<Option> options = []
    File processJsonFile

    OptionsVerifier(File processJsonFile) {
        this.processJsonFile = processJsonFile
    }

    void minHeap(String value) {
        options.add(new MinHeap(value))
    }

    void maxHeap(String value) {
        options.add(new MaxHeap(value))
    }

    void jvmArgs(String value) {
        options.add(new JvmArg(value))
    }

    void systemProperty(String name, String value) {
        options.add(new SystemProperty(name, value))
    }

    void environmentVariable(String name, String value) {
        options.add(new EnvironmentVariable(name, value))
    }

    void defaultCharacterEncoding(String value) {
        options.add(new DefaultCharacterEncoding(value))
    }

    void enableAssertions() {
        options.add(new EnableAssertions())
    }

    void bootstrapClasspath(String path) {
        options.add(new BootstrapClassPath(path))
    }

    String toDsl() {
        options.collect { it.dsl }.join("\n")
    }

    void verifyAllOptions() {
        def processEnv = new JsonSlurper().parse(processJsonFile)
        options.each {
            it.verify(processEnv)
        }
    }

    String dumpProcessEnvironment(boolean includeBootstrapClasspath) {
        return """
            Map processEnv = [:]

            RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
            List<String> arguments = runtimeMxBean.getInputArguments();

            processEnv["${ARGUMENTS}"] = arguments

            if (${includeBootstrapClasspath}) {
                processEnv["${BOOTSTRAP_CLASSPATH}"] = runtimeMxBean.getBootClassPath().replaceAll(Pattern.quote(File.separator),'/')
            }

            processEnv["${ENV_VARIABLES}"] = [:]
            System.getenv().each { key, value ->
                processEnv["${ENV_VARIABLES}"][key] = value
            }

            new File('${TextUtil.normaliseFileSeparators(processJsonFile.absolutePath)}').text = groovy.json.JsonOutput.toJson(processEnv)
        """
    }

    interface Option {
        String getDsl();

        void verify(Map processEnv);
    }

    class MinHeap implements Option {
        String value

        MinHeap(String value) {
            this.value = value
        }

        @Override
        String getDsl() {
            return "minHeapSize = '${value}'"
        }

        @Override
        void verify(Map processEnv) {
            assert processEnv[ARGUMENTS].contains("-Xms${value}".toString())
        }
    }

    class MaxHeap implements Option {
        String value

        MaxHeap(String value) {
            this.value = value
        }

        @Override
        String getDsl() {
            return "maxHeapSize = '${value}'"
        }

        @Override
        void verify(Map processEnv) {
            assert processEnv[ARGUMENTS].contains("-Xmx${value}".toString())
        }
    }

    class JvmArg implements Option {
        String value

        JvmArg(String value) {
            this.value = value
        }

        @Override
        String getDsl() {
            return "jvmArgs('${value}')"
        }

        @Override
        void verify(Map processEnv) {
            assert processEnv[ARGUMENTS].contains(value)
        }
    }

    class SystemProperty implements Option {
        String name
        String value

        SystemProperty(String name, String value) {
            this.name = name
            this.value = value
        }

        @Override
        String getDsl() {
            return "systemProperty('${name}', '${value}')"
        }

        @Override
        void verify(Map processEnv) {
            assert processEnv[ARGUMENTS].contains("-D${name}=${value}".toString())
        }
    }

    class EnvironmentVariable implements Option {
        String name
        String value

        EnvironmentVariable(String name, String value) {
            this.name = name
            this.value = value
        }

        @Override
        String getDsl() {
            return "environment('${name}', '${value}')"
        }

        @Override
        void verify(Map processEnv) {
            assert processEnv[ENV_VARIABLES].containsKey(name) && processEnv[ENV_VARIABLES][name] == value
        }
    }

    class DefaultCharacterEncoding implements Option {
        String value

        DefaultCharacterEncoding(String value) {
            this.value = value
        }

        @Override
        String getDsl() {
            return "defaultCharacterEncoding  = '${value}'"
        }

        @Override
        void verify(Map processEnv) {
            assert processEnv[ARGUMENTS].contains("-Dfile.encoding=${value}".toString())
        }
    }

    class EnableAssertions implements Option {
        @Override
        String getDsl() {
            return "enableAssertions = true"
        }

        @Override
        void verify(Map processEnv) {
            assert processEnv[ARGUMENTS].contains('-ea')
        }
    }

    class BootstrapClassPath implements Option {
        String path

        BootstrapClassPath(String path) {
            this.path = path
        }

        @Override
        String getDsl() {
            return """
                if (fileTree != null) { bootstrapClasspath = fileTree }
                bootstrapClasspath(new File('${path}'))
            """
        }

        @Override
        void verify(Map processEnv) {
            processEnv[BOOTSTRAP_CLASSPATH].endsWith(path)
        }
    }
}
