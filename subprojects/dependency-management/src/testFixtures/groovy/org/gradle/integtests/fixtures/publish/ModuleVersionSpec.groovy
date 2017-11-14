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

package org.gradle.integtests.fixtures.publish

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class ModuleVersionSpec {
    private final String groupId
    private final String artifactId
    private final String version

    private final List<Object> dependsOn = []
    private Expectation expectGetMetadata = Expectation.NONE
    private boolean expectGetArtifact

    enum Expectation {
        GET,
        MAYBE,
        NONE
    }

    ModuleVersionSpec(String group, String module, String version) {
        this.groupId = group
        this.artifactId = module
        this.version = version
    }

    void expectGetMetadata() {
        expectGetMetadata = Expectation.GET
    }

    void expectGetArtifact() {
        expectGetArtifact = true
    }

    void maybeGetMetadata() {
        expectGetMetadata = Expectation.MAYBE
    }

    void dependsOn(coord) {
        dependsOn << coord
    }

    void build(MavenHttpRepository repository) {
        def module = repository.module(groupId, artifactId, version)
        def gradleMetadataEnabled = GradleMetadataResolveRunner.isGradleMetadataEnabled()
        if (gradleMetadataEnabled) {
            module.withModuleMetadata()
        }
        switch (expectGetMetadata) {
            case Expectation.NONE:
                break;
            case Expectation.MAYBE:
                if (GradleContextualExecuter.parallel) {
                    module.pom.allowGetOrHead()
                    if (gradleMetadataEnabled) {
                        module.moduleMetadata.allowGetOrHead()
                    }
                }
                break
            default:
                module.pom.expectGet()
                if (gradleMetadataEnabled) {
                    module.moduleMetadata.expectGet()
                }
        }


        if (expectGetArtifact) {
            module.artifact.expectGet()
        }
        if (dependsOn) {
            dependsOn.each {
                if (it instanceof CharSequence) {
                    def args = it.split(':') as List
                    module.dependsOn(repository.module(*args))
                } else if (it instanceof Map) {
                    def other = repository.module(it.group, it.artifact, it.version)
                    module.dependsOn(it, other)
                } else {
                    module.dependsOn(it)
                }
            }
        }
        module.publish()
    }

}
