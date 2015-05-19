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

package org.gradle.api.reporting.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class ModelReportIntegrationTest extends AbstractIntegrationSpec {

    def "displays basic structure of an empty project"() {
        given:
        buildFile << initTasks()

        when:
        run "model"

        then:
        output.contains(toPlatformLineSeparators(
            """model
    tasks
        components = task ':components'
        dependencies = task ':dependencies'
        dependencyInsight = task ':dependencyInsight'
        help = task ':help'
        init = task ':init'
        model = task ':model'
        projects = task ':projects'
        properties = task ':properties'
        tasks = task ':tasks'
        wrapper = task ':wrapper'
"""))
    }

    def "displays basic structure of a polyglot project"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
    id 'cpp'
    id 'c'
}

model {
    components {
        jvmLib(JvmLibrarySpec)
        nativeLib(NativeLibrarySpec)
    }
}
"""
        buildFile << initTasks()
        when:
        run "model"

        then:
        def report = new ConsoleReportOutput(output)
        report.hasTitle('Root project')
        report.hasRootNode('model')
        report.hasNodeStructure(
            """model
    binaries
        jvmLibJar
            tasks
        nativeLibSharedLibrary
            tasks
        nativeLibStaticLibrary
            tasks
    binaryNamingSchemeBuilder
    binarySpecFactory
    buildTypes
    componentSpecFactory
    components
        jvmLib
            binaries
                jvmLibJar
                    tasks
            sources
                java
                resources
        nativeLib
            binaries
                nativeLibSharedLibrary
                    tasks
                nativeLibStaticLibrary
                    tasks
            sources
                c
                cpp
    flavors
    javaToolChain
    jvm
    languageTransforms
    languages
    platformResolver
    platforms
    repositories
    sources
    tasks
        assemble
        build
        check
        clean
        components
        createJvmLibJar
        createNativeLibStaticLibrary
        dependencies
        dependencyInsight
        help
        init
        jvmLibJar
        linkNativeLibSharedLibrary
        model
        nativeLibSharedLibrary
        nativeLibStaticLibrary
        projects
        properties
        tasks
        wrapper
    toolChains"""
        )
    }

    def "displays basic values of a simple model graph with values"() {
        given:
        buildFile << """

@Managed
public interface PasswordCredentials {
    String getUsername()
    String getPassword()
    void setUsername(String s)
    void setPassword(String s)
}


@Managed
public interface Numbers {
    Integer getValue()
    void setValue(Integer i)
}

model {
    primaryCredentials(PasswordCredentials){
        username = 'uname'
        password = 'hunter2'
    }

    nullCredentials(PasswordCredentials) { }
    numbers(Numbers){
        value = 5
    }
}

"""
        buildFile << initTasks()
        when:
        run "model"

        then:

        output.contains(toPlatformLineSeparators(
            """model
    nullCredentials
        password
        username
    numbers
        value = 5
    primaryCredentials
        password = hunter2
        username = uname
    tasks
        components = task ':components'
        dependencies = task ':dependencies'
        dependencyInsight = task ':dependencyInsight'
        help = task ':help'
        init = task ':init'
        model = task ':model'
        projects = task ':projects'
        properties = task ':properties'
        tasks = task ':tasks'
        wrapper = task ':wrapper'
"""))

    }

    private String initTasks() {
        //These are not on the model when run via ./gradlew diagnostics:integTest
        return """
            task wrapper << {}
            task init << {}
        """
    }

}
