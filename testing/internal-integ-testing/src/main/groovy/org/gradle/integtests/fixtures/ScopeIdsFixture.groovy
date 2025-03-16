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

package org.gradle.integtests.fixtures

import groovy.json.JsonSlurper
import groovy.transform.EqualsAndHashCode
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.UserInitScriptExecuterFixture
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.scopeids.id.UserScopeId
import org.gradle.internal.scopeids.id.WorkspaceScopeId
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

/**
 * Extracts the scope IDs for a build, and asserts that all nested builds have the same IDs.
 */
class ScopeIdsFixture extends UserInitScriptExecuterFixture {

    private final List<Map<String, ScopeIds>> idsOfBuildTrees = []

    boolean disableConsistentWorkspaceIdCheck
    boolean disableConsistentUserIdCheck

    ScopeIdsFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    int getBuildCount() {
        idsOfBuildTrees.size()
    }

    List<ScopeIds> getIds() {
        idsOfBuildTrees.collect { it.get(":") }
    }

    Map<String, ScopeIds> idsOfBuildTree(int buildNum) {
        idsOfBuildTrees.get(buildNum)
    }

    ScopeIds ids(int buildNum) {
        assert idsOfBuildTrees.size() > buildNum
        getIds()[buildNum]
    }

    UniqueId getBuildInvocationId() {
        idsOfBuildTrees.last().":".buildInvocation
    }

    UniqueId getWorkspaceId() {
        idsOfBuildTrees.last().":".workspace
    }

    UniqueId getUserId() {
        idsOfBuildTrees.last().":".user
    }

    List<UniqueId> getBuildInvocationIds() {
        new ArrayList<UniqueId>(idsOfBuildTrees*.get(":").buildInvocation)
    }

    List<UniqueId> getWorkspaceIds() {
        new ArrayList<UniqueId>(idsOfBuildTrees*.get(":").workspace)
    }

    List<UniqueId> getUserIds() {
        new ArrayList<UniqueId>(idsOfBuildTrees*.get(":").user)
    }

    List<List<String>> getBuildPaths() {
        idsOfBuildTrees.collect { it.keySet().sort() }
    }

    List<String> lastBuildPaths() {
        getBuildPaths().last()
    }

    @Override
    String initScriptContent() {
        """
            class CollectScopeIds extends DefaultTask {

                private File outputJsonFile

                void setOutputJsonFile(File file) {
                    outputJsonFile = file
                }

                @TaskAction def collect() {
                    def gradle = services.get(Gradle)
                    def scopeIds = [
                        "\${gradle.identityPath}": [
                            buildInvocation: services.get(${BuildInvocationScopeId.name}).id.asString(),
                            workspace: services.get(${WorkspaceScopeId.name}).id.asString(),
                            user: services.get(${UserScopeId.name}).id.asString()
                        ]
                    ]
                    outputJsonFile << groovy.json.JsonOutput.toJson(scopeIds) + '\\n'
                }
            }

            rootProject {
                task collectScopeIds(type: CollectScopeIds) {
                    outputJsonFile = new File("${normaliseFileSeparators(idsFile.absolutePath)}")
                }
                tasks.withType(DefaultTask) {
                    if (name != "collectScopeIds") {
                        dependsOn collectScopeIds
                    }
                }
            }
        """
    }

    @Override
    void configureExecuter(GradleExecuter executer) {
        super.configureExecuter(executer)
        executer.beforeExecute {
            idsFile.delete()
        }
    }

    private TestFile getIdsFile() {
        testDir.testDirectory.file("ids.json")
    }

    @Override
    void afterBuild() {
        Map<String, Map<String, String>> idsMap = idsFile.readLines()
            .collect { line -> new JsonSlurper().parse(new StringReader(line)) as Map<String, Map<String, String>> }
            .collectMany { it.entrySet() }
            .collectEntries { it }
        Map<String, ScopeIds> ids = [:]
        idsMap.each {
            ids[it.key] = new ScopeIds(
                UniqueId.from(it.value.buildInvocation),
                UniqueId.from(it.value.workspace),
                UniqueId.from(it.value.user)
            )
        }

        // Assert that same IDs were used for all builds in build
        def allScopeIds = ids.values()
        def buildInvocationIds = allScopeIds*.buildInvocation

        assert buildInvocationIds.unique(false).size() == 1

        if (!disableConsistentWorkspaceIdCheck) {
            def workspaceIds = allScopeIds*.workspace
            assert workspaceIds.unique(false).size() == 1
        }

        if (!disableConsistentUserIdCheck) {
            def userIds = allScopeIds*.user
            assert userIds.unique(false).size() == 1
        }

        this.idsOfBuildTrees << ids

        // Assert that unique build invocation ID was used
        this.idsOfBuildTrees.collect { it.values().first().buildInvocation }.unique(false).size() == this.idsOfBuildTrees.size()

    }

    @EqualsAndHashCode
    static class ScopeIds {
        final UniqueId buildInvocation
        final UniqueId workspace
        final UniqueId user

        ScopeIds(UniqueId buildInvocation, UniqueId workspace, UniqueId user) {
            this.buildInvocation = buildInvocation
            this.workspace = workspace
            this.user = user
        }

        @Override
        String toString() {
            return "ScopeIds{" + "build=" + buildInvocation + ", workspace=" + workspace + ", user=" + user + '}';
        }
    }

}
