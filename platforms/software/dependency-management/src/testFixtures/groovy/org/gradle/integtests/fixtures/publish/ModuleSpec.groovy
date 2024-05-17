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
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule

class ModuleSpec {
    private final String groupId
    private final String artifactId
    private final Map<String, ModuleVersionSpec> versions = [:].withDefault { new ModuleVersionSpec(groupId, artifactId, it) }

    private List<InteractionExpectation> metadataExpectations = []

    ModuleSpec(String group, String name) {
        groupId = group
        artifactId = name
    }

    void version(String version, @DelegatesTo(value=ModuleVersionSpec, strategy = Closure.DELEGATE_ONLY) Closure<Void> versionSpec = {}) {
        versionSpec.delegate = versions[version]
        versionSpec.resolveStrategy = Closure.DELEGATE_ONLY
        versionSpec()
    }

    void build(HttpRepository repository) {
        versions.values()*.build(repository)
        metadataExpectations.each {
            def module = repository.module(groupId, artifactId)
            if (module instanceof MavenModule) {
                switch (it) {
                    case InteractionExpectation.GET:
                        module.rootMetaData.expectGet()
                        break
                    case InteractionExpectation.HEAD:
                        module.rootMetaData.expectHead()
                        break
                    case InteractionExpectation.MAYBE:
                        module.rootMetaData.allowAll()
                        break
                }

            } else if (module instanceof IvyModule) {
                def directoryList = repository.directoryList(module.organisation, module.module)
                switch (it) {
                    case InteractionExpectation.GET:
                        directoryList.expectGet()
                        break
                    case InteractionExpectation.HEAD:
                        directoryList.expectHead()
                        break
                    case InteractionExpectation.MAYBE:
                        directoryList.allowAll()
                        break
                }
            }
        }
    }

    void expectVersionListing() {
        expectGetMetadata()
    }

    void expectHeadVersionListing() {
        if (GradleMetadataResolveRunner.useIvy()) {
            metadataExpectations << InteractionExpectation.GET
        } else {
            metadataExpectations << InteractionExpectation.HEAD
        }
    }

    void expectGetMetadata() {
        metadataExpectations << InteractionExpectation.GET
    }

    void methodMissing(String name, args) {
        Closure spec = {}
        if (args && args.length == 1 && args[0] instanceof Closure) {
            spec = args[0]
        }
        version(name, spec)
    }

}
