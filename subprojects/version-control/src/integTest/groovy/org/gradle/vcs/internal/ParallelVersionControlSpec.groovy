/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.vcs.fixtures.GitFileRepository
import org.gradle.vcs.git.internal.DefaultGitVersionControlSpec
import org.junit.Rule

class ParallelVersionControlSpec extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    @Rule GitFileRepository repo = new GitFileRepository(temporaryFolder.getTestDirectory())

    def projects = ['A', 'B', 'C', 'D']

    def setup() {
        server.start()

        multiProjectBuild('many-clones', projects) {
            buildFile << """
            import ${VersionControlSystemFactory.canonicalName}
            import ${DefaultGitVersionControlSpec.canonicalName}

            class GitClone extends DefaultTask {
                private final VersionControlSystemFactory versionControlSystemFactory
                
                URI url = project.uri('${repo.url}')

                File outputDir
                
                @javax.inject.Inject
                GitClone(VersionControlSystemFactory versionControlSystemFactory) {
                    this.versionControlSystemFactory = versionControlSystemFactory
                }

                @TaskAction
                void populate() {
                    def spec = new DefaultGitVersionControlSpec(project.gradle.startParameter, project.gradle.classLoaderScope)
                    spec.url = url
                    def system = versionControlSystemFactory.create(spec)
                    def ref = system.getDefaultBranch(spec)
                    system.populate(outputDir, ref, spec)
                    assert new File(outputDir, 'repo/.git').exists()
                }
            }
            """.stripIndent()
        }
    }

    def "can populate into same dir in parallel"() {
        given:
        projects.each { p ->
            buildFile << """
            project('$p') {
              task clone(type:GitClone) {
                outputDir = rootProject.file('build/target')
                doFirst {
                    ${server.callFromBuild(p)}
                }
              }
            }
            """.stripIndent()
        }
        server.expectConcurrent(projects)

        def source = repo.workTree.file('source')
        source.text = 'hello world'
        repo.commit('initial commit')

        expect:
        succeeds('clone', '--parallel', '--max-workers=4')
    }

    def "can clone into different dirs in parallel"() {
        given:
        projects.each { p ->
            buildFile << """
            project('$p') {
              task clone(type:GitClone) {
                outputDir = rootProject.file('build/target${p}')
                doFirst {
                    ${server.callFromBuild(p)}
                }
              }
            }
            """.stripIndent()
        }
        server.expectConcurrent(projects)

        def source = repo.workTree.file('source')
        source.text = 'hello world'
        repo.commit('initial commit')

        expect:
        succeeds('clone', '--parallel', '--max-workers=4')
    }
}
