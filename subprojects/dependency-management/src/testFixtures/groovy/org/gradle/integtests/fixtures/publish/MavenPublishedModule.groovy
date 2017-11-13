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
import org.gradle.test.fixtures.maven.DelegatingMavenModule
import org.gradle.test.fixtures.server.http.MavenHttpModule

class MavenPublishedModule extends DelegatingMavenModule<MavenHttpModule> {
    private final MavenHttpModule module

    MavenPublishedModule(MavenHttpModule module) {
        super(module)
        this.module = module
    }

    void assertGetRootMetadata() {
        if (GradleContextualExecuter.parallel) {
            module.rootMetaData.allowGetOrHead()
        } else {
            module.rootMetaData.expectGet()
        }
    }

    void maybeGetMetadata() {
        if (GradleContextualExecuter.parallel) {
            module.allowAll()
        }
    }

    void assertGetMetadata() {
        if (GradleContextualExecuter.parallel) {
            module.allowAll()
        } else {
            module.pom.expectGet()
            if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                module.moduleMetadata.expectGet()
            }
        }
    }

    void assertGetArtifact() {
        if (GradleContextualExecuter.parallel) {
            module.allowAll()
        } else {
            module.artifact.expectGet()
        }
    }

}
