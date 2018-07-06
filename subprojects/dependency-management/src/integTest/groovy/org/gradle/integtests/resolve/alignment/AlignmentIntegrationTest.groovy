/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.alignment

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class AlignmentIntegrationTest extends AbstractModuleDependencyResolveTest {


    def "should align leaves to the same version"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.1'
            path 'json:1.1 -> core:1.1'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.1'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            'org:core:1.0' {
                expectGetMetadata()
            }
            'org:xml:1.0' {
                expectGetMetadata()
            }
            'org:core:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1' {
                expectResolve()
            }
            'org:platform:1.0'(virtualPlatform)
            'org:platform:1.1'(virtualPlatform)
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:xml:1.0", "org:xml:1.1") {
                    byConstraint("belongs to platform org:platform:1.1")
                    module('org:core:1.1')
                }
                module("org:json:1.1") {
                    module('org:core:1.1')
                }
            }
        }
    }

    def "should not align modules outside of platform"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.1'
            path 'json:1.1 -> core:1.1'
            path 'outside:module:1.0 -> core:1.0'
            path 'outside:module:1.1 -> core:1.0'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.1'
                conf 'outside:module:1.0'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            'org:core:1.0' {
                expectGetMetadata()
            }
            'org:xml:1.0' {
                expectGetMetadata()
            }
            'org:core:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1' {
                expectResolve()
            }
            'org:platform:1.0'(virtualPlatform)
            'org:platform:1.1'(virtualPlatform)

            'outside:module:1.0' {
                // this will NOT upgrade to 1.1, despite core being 1.1, because this module
                // does not belong to the same platform
                expectResolve()
            }
            'outside:platform:1.0'(virtualPlatform)
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:xml:1.0", "org:xml:1.1") {
                    byConstraint("belongs to platform org:platform:1.1")
                    module('org:core:1.1')
                }
                module("org:json:1.1") {
                    module('org:core:1.1')
                }
                module("outside:module:1.0") {
                    edge('org:core:1.0', 'org:core:1.1').byConflictResolution("between versions 1.0 and 1.1")
                }
            }
        }
    }

    def "should align leaf with core"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.1'
            path 'json:1.1 -> core:1.1'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.0'
                conf 'org:core:1.1'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            'org:xml:1.0' {
                expectGetMetadata()
            }
            'org:json:1.0' {
                expectGetMetadata()
            }
            'org:core:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1' {
                expectResolve()
            }
            'org:platform:1.0'(virtualPlatform)
            'org:platform:1.1'(virtualPlatform)
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:xml:1.0", "org:xml:1.1") {
                    byConstraint("belongs to platform org:platform:1.1")
                    module('org:core:1.1')
                }
                edge("org:json:1.0", "org:json:1.1") {
                    byConstraint("belongs to platform org:platform:1.1")
                    module('org:core:1.1')
                }
                module('org:core:1.1')
            }
        }
    }

    def "shouldn't fail if target alignment version doesn't exist"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'json:1.1 -> core:1.1'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.1'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            'org:core:1.0' {
                expectGetMetadata()
            }
            'org:xml:1.0' {
                expectResolve()
            }
            'org:core:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1'(virtualPlatform)
            'org:platform:1.0'(virtualPlatform)
            'org:platform:1.1'(virtualPlatform)
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:xml:1.0") {
                    edge('org:core:1.0', 'org:core:1.1')
                        .byConflictResolution("between versions 1.0 and 1.1")
                        .byConstraint("belongs to platform org:platform:1.1")
                }
                module("org:json:1.1") {
                    module('org:core:1.1')
                }
            }
        }
    }

    def "should align leaves to the same version when core has lower version"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.0' // patch releases
            path 'json:1.1 -> core:1.0' // patch releases
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.1'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            'org:xml:1.0' {
                expectGetMetadata()
            }
            'org:core:1.0' {
                expectResolve()
            }
            'org:core:1.1'(virtualPlatform)
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1' {
                expectResolve()
            }
            'org:platform:1.0'(virtualPlatform)
            'org:platform:1.1'(virtualPlatform)
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:xml:1.0", "org:xml:1.1") {
                    module('org:core:1.0')
                    byConstraint("belongs to platform org:platform:1.1")
                }
                module("org:json:1.1") {
                    module('org:core:1.0')
                }
            }
        }
    }

    /**
     * This test demonstrates a real world example, where some published versions are heterogeneous
     * or even inconsistent. For example, databind 2.9.4 depends on annotations 2.9.0, but we still
     * want to upgrade annotations to the highest version of the platform seen in the graph, which
     * is 2.9.4.1, a **patch** release in this case. This patch release doesn't publish all versions
     */
    def "can align heterogeneous versions"() {
        repository {
            path 'databind:2.7.9 -> core:2.7.9'
            path 'databind:2.7.9 -> annotations:2.7.9'
            path 'databind:2.9.4 -> core:2.9.4'
            path 'databind:2.9.4 -> annotations:2.9.0' // intentional!
            path 'kt:2.9.4.1 -> databind:2.9.4'
            'org:annotations:2.9.0'()
            'org:annotations:2.9.4'()
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:core:2.9.4'
                conf 'org:databind:2.7.9'
                conf 'org:kt:2.9.4.1'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            'org:core:2.9.4' {
                expectResolve()
            }
            'org:databind:2.7.9' {
                expectGetMetadata()
            }
            'org:kt:2.9.4.1' {
                expectResolve()
            }
            'org:core:2.9.4.1'(virtualPlatform)
            'org:databind:2.9.4.1'(virtualPlatform)
            'org:annotations:2.9.4.1'(virtualPlatform)
            'org:databind:2.9.4' {
                expectResolve()
            }
            'org:annotations:2.9.4' {
                expectResolve()
            }
            'org:platform:2.9.4.1'(virtualPlatform)
            'org:platform:2.9.4'(virtualPlatform)
            'org:platform:2.9.0'(virtualPlatform)
            'org:platform:2.7.9'(virtualPlatform)
            'org:annotations:2.7.9' {
                expectGetMetadata()
            }
            'org:annotations:2.9.0' {
                expectGetMetadata()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:core:2.9.4')
                edge('org:databind:2.7.9', 'org:databind:2.9.4').byConstraint("belongs to platform org:platform:2.9.4.1")
                module('org:kt:2.9.4.1') {
                    module('org:databind:2.9.4') {
                        module('org:core:2.9.4').byConstraint("belongs to platform org:platform:2.9.4.1")
                        edge('org:annotations:2.9.0', 'org:annotations:2.9.4').byConstraint("belongs to platform org:platform:2.9.4.1")
                    }
                }
            }
        }

    }

    /**
     * This test is a variant of the previous one where there's an additional catch: one
     * of the modules (annotations) is supposely inexistent in 2.7.9 (say, it appeared in 2.9.x)
     */
    def "can align heterogeneous versions with new modules appearing in later releases"() {
        repository {
            path 'databind:2.7.9 -> core:2.7.9'
            path 'databind:2.9.4 -> core:2.9.4'
            path 'databind:2.9.4 -> annotations:2.9.0' // intentional!
            path 'kt:2.9.4.1 -> databind:2.9.4'
            'org:annotations:2.9.0'()
            'org:annotations:2.9.4'()
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:core:2.9.4'
                conf 'org:databind:2.7.9'
                conf 'org:kt:2.9.4.1'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            'org:core:2.9.4' {
                expectResolve()
            }
            'org:databind:2.7.9' {
                expectGetMetadata()
            }
            'org:kt:2.9.4.1' {
                expectResolve()
            }
            'org:core:2.9.4.1'(virtualPlatform)
            'org:databind:2.9.4.1'(virtualPlatform)
            'org:annotations:2.9.4.1'(virtualPlatform)
            'org:databind:2.9.4' {
                expectResolve()
            }
            'org:annotations:2.9.4' {
                expectResolve()
            }
            'org:platform:2.9.4.1'(virtualPlatform)
            'org:platform:2.9.4'(virtualPlatform)
            'org:platform:2.9.0'(virtualPlatform)
            'org:platform:2.7.9'(virtualPlatform)
            'org:annotations:2.9.0' {
                expectGetMetadata()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:core:2.9.4').byConstraint("belongs to platform org:platform:2.9.4.1")
                edge('org:databind:2.7.9', 'org:databind:2.9.4').byConstraint("belongs to platform org:platform:2.9.4.1")
                module('org:kt:2.9.4.1') {
                    module('org:databind:2.9.4') {
                        module('org:core:2.9.4').byConstraint("belongs to platform org:platform:2.9.4.1")
                        edge('org:annotations:2.9.0', 'org:annotations:2.9.4').byConstraint("belongs to platform org:platform:2.9.4.1")
                    }
                }
            }
        }

    }

    @RequiredFeatures([
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    ])
    def "can align thanks to a published platform"() {
        repository {
            path 'databind:2.7.9 -> core:2.7.9'
            path 'databind:2.7.9 -> annotations:2.7.9'
            path 'databind:2.9.4 -> core:2.9.4'
            path 'databind:2.9.4 -> annotations:2.9.0' // intentional!
            path 'kt:2.9.4.1 -> databind:2.9.4'
            'org:annotations:2.9.0'()
            'org:annotations:2.9.4'()

            // define "real" platforms, as published modules.
            // The platforms are supposed to declare _in extenso_ what modules
            // they include, by constraints
            'org:platform' {
                '2.7.9' {
                    constraint("org:databind:2.7.9")
                    constraint("org:core:2.7.9")
                    constraint("org:annotations:2.7.9")
                }
                '2.9.0' {
                    constraint("org:databind:2.9.0")
                    constraint("org:core:2.9.0")
                    constraint("org:annotations:2.9.0")
                }
                '2.9.4' {
                    constraint("org:databind:2.9.4")
                    constraint("org:core:2.9.4")
                    constraint("org:annotations:2.9.0")
                }
                '2.9.4.1' {
                    // versions here are intentionally lower
                    constraint("org:databind:2.9.4")
                    constraint("org:core:2.9.4")
                    constraint("org:annotations:2.9.4")
                }
            }
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:core:2.9.4'
                conf 'org:databind:2.7.9'
                conf 'org:kt:2.9.4.1'
                
                components.all(DeclarePlatform)
            }
            
            class DeclarePlatform implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        belongsTo("org:platform:\${id.version}")
                    }
                }
            }
        """


        when:
        repositoryInteractions {
            'org:core:2.9.4' {
                expectResolve()
            }
            'org:databind:2.7.9' {
                expectGetMetadata()
            }
            'org:kt:2.9.4.1' {
                expectResolve()
            }
            'org:databind:2.9.4' {
                expectResolve()
            }
            'org:annotations:2.9.4' {
                expectResolve()
            }
            'org:platform:2.7.9' {
                expectGetMetadata()
            }
            'org:platform:2.9.4.1' {
                expectGetMetadata()
            }
            'org:platform:2.9.4' {
                expectGetMetadata()
            }
            'org:annotations:2.7.9' {
                expectGetMetadata()
            }
            'org:annotations:2.9.0' {
                expectGetMetadata()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:core:2.9.4')
                edge('org:databind:2.7.9', 'org:databind:2.9.4')
                module('org:kt:2.9.4.1') {
                    module('org:databind:2.9.4') {
                        module('org:core:2.9.4')
                        edge('org:annotations:2.9.0', 'org:annotations:2.9.4')
                    }
                }
            }
        }

    }

    def "can align 2 different platforms"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.1'
            path 'json:1.1 -> core:1.1'

            path 'org2:xml:1.0 -> org2:core:1.0'
            path 'org2:json:1.0 -> org2:core:1.0'
            path 'org2:xml:1.1 -> org2:core:1.1'
            path 'org2:json:1.1 -> org2:core:1.1'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.1'

                conf 'org2:xml:1.0'
                conf 'org2:json:1.1'
            }
        """
        and:
        "a rule which infers module set from group and version"()

        when:
        repositoryInteractions {
            ['org', 'org2'].each { group ->
                "$group:core:1.0" {
                    expectGetMetadata()
                }
                "$group:xml:1.0" {
                    expectGetMetadata()
                }
                "$group:core:1.1" {
                    expectResolve()
                }
                "$group:json:1.1" {
                    expectResolve()
                }
                "$group:xml:1.1" {
                    expectResolve()
                }
                "$group:platform:1.0" {
                    expectGetMetadataMissing()
                    if (!GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled) {
                        expectHeadArtifactMissing()
                    }
                }
                "$group:platform:1.1" {
                    expectGetMetadataMissing()
                    if (!GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled) {
                        expectHeadArtifactMissing()
                    }
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:xml:1.0", "org:xml:1.1") {
                    byConstraint("belongs to platform org:platform:1.1")
                    module('org:core:1.1')
                }
                module("org:json:1.1") {
                    module('org:core:1.1')
                }

                edge("org2:xml:1.0", "org2:xml:1.1") {
                    byConstraint("belongs to platform org2:platform:1.1")
                    module('org2:core:1.1')
                }
                module("org2:json:1.1") {
                    module('org2:core:1.1')
                }
            }
        }
    }

    def "doesn't align on evicted edge"() {
        given:
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.1'
            path 'json:1.1 -> core:1.1'

            path 'org2:foo:1.0 -> org4:a:1.0 -> org:json:1.1'
            path 'org3:bar:1.0 -> org4:b:1.1 -> org4:a:1.1'
        }

        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org2:foo:1.0'
                conf 'org3:bar:1.0'
            }
        """

        and:
        "align the 'org' group only"()

        when:
        repositoryInteractions {
            'org:xml:1.0' {
                expectResolve()
            }
            'org:core:1.0' {
                expectResolve()
            }
            'org2:foo:1.0' {
                expectResolve()
            }
            'org3:bar:1.0' {
                expectResolve()
            }
            'org4:a:1.0' {
                expectGetMetadata()
            }
            'org4:a:1.1' {
                expectResolve()
            }
            'org4:b:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectGetMetadata()
            }
            'org:platform:1.0'(virtualPlatform)

        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:xml:1.0') {
                    module('org:core:1.0')
                }
                module('org2:foo:1.0') {
                    edge('org4:a:1.0', 'org4:a:1.1') {
                        byConflictResolution("between versions 1.0 and 1.1")
                    }
                }
                module('org3:bar:1.0') {
                    module('org4:b:1.1') {
                        module('org4:a:1.1')
                    }
                }
            }
        }
    }

    private void "a rule which infers module set from group and version"() {
        buildFile << """
            dependencies {
                components.all(InferModuleSetFromGroupAndVersion)
            }
            
            class InferModuleSetFromGroupAndVersion implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        belongsTo("\${id.group}:platform:\${id.version}")
                    }
                }
            }
        """
    }

    private void "align the 'org' group only"() {
        buildFile << """
            dependencies {
                components.all(AlignOrgGroup)
            }
            
            class AlignOrgGroup implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        if ('org' == id.group) {
                           belongsTo("\${id.group}:platform:\${id.version}")
                        }
                    }
                }
            }
        """
    }

    final Closure<Void> virtualPlatform = {
        expectGetMetadataMissing()
        if (!GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled) {
            expectHeadArtifactMissing()
        }
    }
}
