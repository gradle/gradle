/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.inputs.process

import org.gradle.configurationcache.fixtures.ExternalProcessFixture.Snippets
import org.gradle.configurationcache.fixtures.ExternalProcessFixture.SnippetsFactory
import org.gradle.configurationcache.fixtures.TransformFixture
import org.gradle.process.ExecOperations

import javax.inject.Inject

import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.exec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.javaexec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.processBuilder

class ProcessInTransformIntegrationTest extends AbstractProcessIntegrationTest {
    def "using #snippetsFactory.summary in transform action with #task is not a problem"(SnippetsFactory snippetsFactory, String task) {
        given:
        getTransformFixture(snippetsFactory.newSnippets(execOperationsFixture)).tap {
            withTransformPlugin(testDirectory.createDir("buildSrc"))
            withJavaLibrarySubproject(testDirectory.createDir("subproject"))
        }

        settingsFile("""
            include("subproject")
        """)

        buildFile("""
            plugins { id("${TransformFixture.TRANSFORM_PLUGIN_ID}") }
            ${mavenCentralRepository()}
            dependencies {
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} "org.apache.commons:commons-math3:3.6.1"
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} project(":subproject")
            }
        """)

        when:
        configurationCacheRun(":$task")

        then:
        outputContains("Hello")

        where:
        [snippetsFactory, task] << [
            [
                exec("getExecOperations()").java,
                javaexec("getExecOperations()").java,
                processBuilder().java
            ],
            [
                "resolveCollection",
                "resolveProviders"
            ]
        ].combinations()
    }

    def "using #snippetsFactory.summary in transform action with #task of buildSrc build is not a problem"(SnippetsFactory snippetsFactory, String task) {
        given:
        createDir("buildSrc") {
            getTransformFixture(snippetsFactory.newSnippets(execOperationsFixture)).tap {
                withTransformPlugin(dir("transform-plugin"))
                withJavaLibrarySubproject(dir("subproject"))
            }

            file("settings.gradle") << """
                pluginManagement { includeBuild("transform-plugin") }
                include("subproject")
            """

            file("build.gradle") << """
            plugins { id("${TransformFixture.TRANSFORM_PLUGIN_ID}") }
            ${mavenCentralRepository()}
            dependencies {
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} "org.apache.commons:commons-math3:3.6.1"
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} project(":subproject")
            }

            tasks.named("classes") {
                dependsOn("$task")
            }
            """
        }

        buildFile("""
        tasks.register("check") {}
        """)
        when:
        configurationCacheRun(":check")

        then:
        outputContains("Hello")

        where:
        [snippetsFactory, task] << [
            [
                exec("getExecOperations()").java,
                javaexec("getExecOperations()").java,
                processBuilder().java
            ],
            [
                "resolveCollection",
                "resolveProviders"
            ]
        ].combinations()
    }

    private TransformFixture getTransformFixture(Snippets snippets) {
        return new TransformFixture(
            """
            import ${Inject.name};
            import ${ExecOperations.name};
            ${snippets.imports}
            """,

            """
            @Inject
            public abstract ExecOperations getExecOperations();
            """,

            snippets.body
        )
    }
}
