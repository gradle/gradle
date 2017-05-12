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
import org.gradle.internal.scopeids.id.BuildScopeId
import org.gradle.internal.scopeids.id.UserScopeId
import org.gradle.internal.scopeids.id.WorkspaceScopeId
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

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

    UniqueId getBuildId() {
        idsOfBuildTrees.last().":".build
    }

    UniqueId getWorkspaceId() {
        idsOfBuildTrees.last().":".workspace
    }

    UniqueId getUserId() {
        idsOfBuildTrees.last().":".user
    }

    List<UniqueId> getBuildIds() {
        new ArrayList<UniqueId>(idsOfBuildTrees*.get(":").build)
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

    private TestFile getIdsFile() {
        testDir.testDirectory.file("ids.json")
    }

    @Override
    String initScriptContent() {
        """
            if (gradle.parent == null) {
                def ids = Collections.synchronizedMap([:])
                gradle.ext.scopeIds = ids
                gradle.buildFinished {
                    gradle.rootProject.file("${TextUtil.normaliseFileSeparators(idsFile.absolutePath)}").text = groovy.json.JsonOutput.toJson(ids)
                }
            }

            gradle.rootProject {
                def rootGradle = gradle
                while (rootGradle.parent != null) {
                    rootGradle = rootGradle.parent
                }           
                rootGradle.ext.scopeIds[gradle.identityPath] = [
                    build: gradle.services.get(${BuildScopeId.name}).id.asString(),
                    workspace: gradle.services.get(${WorkspaceScopeId.name}).id.asString(),
                    user: gradle.services.get(${UserScopeId.name}).id.asString()
                ]
            }
        """
    }

    @Override
    void afterBuild() {
        Map<String, Map<String, String>> idsMap = new JsonSlurper().parse(idsFile) as Map
        Map<String, ScopeIds> ids = [:]
        idsMap.each {
            ids[it.key] = new ScopeIds(
                UniqueId.from(it.value.build),
                UniqueId.from(it.value.workspace),
                UniqueId.from(it.value.user)
            )
        }

        // Assert that same IDs were used for all builds in build
        def allBuildsScopeIds = ids.values()
        def buildIds = allBuildsScopeIds.build

        assert buildIds.unique(false).size() == 1

        if (!disableConsistentWorkspaceIdCheck) {
            def workspaceIds = allBuildsScopeIds.workspace
            assert workspaceIds.unique(false).size() == 1
        }

        if (!disableConsistentUserIdCheck) {
            def userIds = allBuildsScopeIds.user
            assert userIds.unique(false).size() == 1
        }

        this.idsOfBuildTrees << ids

        // Assert that unique build ID was used
        this.idsOfBuildTrees.collect { it.values().first().build }.unique(false).size() == this.idsOfBuildTrees.size()

    }

    @EqualsAndHashCode
    static class ScopeIds {
        final UniqueId build
        final UniqueId workspace
        final UniqueId user

        ScopeIds(UniqueId build, UniqueId workspace, UniqueId user) {
            this.build = build
            this.workspace = workspace
            this.user = user
        }

        @Override
        String toString() {
            return "ScopeIds{" + "build=" + build + ", workspace=" + workspace + ", user=" + user + '}';
        }
    }

}
