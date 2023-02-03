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

import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

abstract class AbstractAlignmentSpec extends AbstractModuleDependencyResolveTest {
    static class ModuleAlignmentSpec {
        String group = 'org'
        String name
        List<String> seenVersions = []
        List<String> misses = []
        String alignsTo
        List<String> virtualPlatforms = []
        List<String> publishedPlatforms = []

        ModuleAlignmentSpec group(String group) {
            this.group = group
            this
        }

        ModuleAlignmentSpec name(String name) {
            this.name = name
            this
        }

        ModuleAlignmentSpec tries(String... versions) {
            Collections.addAll(seenVersions, versions)
            this
        }

        ModuleAlignmentSpec misses(String... versions) {
            Collections.addAll(misses, versions)
            this
        }

        ModuleAlignmentSpec alignsTo(String version) {
            this.alignsTo = version
            this
        }

        ModuleAlignmentSpec byVirtualPlatform(String group = 'org', String name = 'platform') {
            virtualPlatforms << "${group}:${name}"
            this
        }

        ModuleAlignmentSpec byPublishedPlatform(String group = 'org', String name = 'platform', String version = null) {
            if (version) {
                publishedPlatforms << "${group}:${name}:${version}"
            } else {
                publishedPlatforms << "${group}:${name}"
            }
            this
        }


        void applyTo(RemoteRepositorySpec spec) {
            def moduleName = name
            def alignedTo = alignsTo
            def otherVersions = seenVersions
            otherVersions.remove(alignedTo)
            def missedVersions = misses
            spec.group(group) {
                module(moduleName) {
                    if (alignedTo) {
                        version(alignedTo) {
                            expectResolve()
                        }
                    }
                    otherVersions.each {
                        version(it) {
                            expectGetMetadata()
                        }
                    }
                    missedVersions.each {
                        version(it) {
                            expectGetMetadataMissing()
                        }
                    }
                }
            }
        }
    }

    public final static Closure<Void> VIRTUAL_PLATFORM = {
        // If the platform is declared as virtual, we won't fetch metadata
    }

    void expectAlignment(@DelegatesTo(value = AlignmentSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def align = new AlignmentSpec()
        spec.delegate = align
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        repositoryInteractions {
            align.applyTo(repoSpec)
        }
    }

    static class AlignmentSpec {
        final List<ModuleAlignmentSpec> specs = []
        final Set<String> skipsPlatformMetadata = []

        AbstractAlignmentSpec.ModuleAlignmentSpec module(String name, @DelegatesTo(value=AbstractAlignmentSpec.ModuleAlignmentSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> config = null) {
            def spec = new AbstractAlignmentSpec.ModuleAlignmentSpec(name: name)
            if (config) {
                config.delegate = spec
                config.resolveStrategy = Closure.DELEGATE_FIRST
                config()
            }
            specs << spec
            spec
        }

        void doesNotGetPlatform(String group = 'org', String name = 'platform', String version = '1.0') {
            skipsPlatformMetadata << "$group:$name:$version"
        }

        void applyTo(RemoteRepositorySpec spec) {
            Set<String> virtualPlatforms = [] as Set
            Set<String> publishedPlatforms = [] as Set
            Set<String> resolvesToVirtual = [] as Set

            specs.each {
                it.applyTo(spec)
                if (it.virtualPlatforms) {
                    it.seenVersions.each { v ->
                        it.virtualPlatforms.each { vp ->
                            virtualPlatforms << "${vp}:$v"
                        }
                    }
                    it.virtualPlatforms.each { vp ->
                        resolvesToVirtual << "${vp}:$it.alignsTo"
                    }
                }
                if (it.publishedPlatforms) {
                    def exactPlatforms = it.publishedPlatforms.findAll { it.count(':') == 2 }
                    def inferredPlatforms = it.publishedPlatforms - exactPlatforms
                    // for published platforms, we know there's no artifacts, so it's actually easier
                    it.seenVersions.each { v ->
                        inferredPlatforms.each { pp ->
                            publishedPlatforms << "${pp}:$v"
                        }
                    }
                    inferredPlatforms.each { pp ->
                        publishedPlatforms << "${pp}:$it.alignsTo"
                    }
                    exactPlatforms.each { pp ->
                        publishedPlatforms << pp
                    }
                }
            }
            virtualPlatforms.remove(resolvesToVirtual)
            virtualPlatforms.removeAll(skipsPlatformMetadata)
            resolvesToVirtual.removeAll(skipsPlatformMetadata)
            publishedPlatforms.removeAll(skipsPlatformMetadata)
            virtualPlatforms.each { p ->
                spec."$p"(VIRTUAL_PLATFORM)
            }
            publishedPlatforms.each { p ->
                spec."$p" {
                    expectGetMetadata()
                }
            }
            resolvesToVirtual.each {
                spec."$it"(VIRTUAL_PLATFORM)
            }
        }

    }

    protected void "a rule which infers module set from group and version"(boolean virtual = true) {
        buildFile << """
            dependencies {
                components.all(InferModuleSetFromGroupAndVersion)
            }
            
            class InferModuleSetFromGroupAndVersion implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        belongsTo("\${id.group}:platform:\${id.version}", ${virtual})
                    }
                }
            }
        """
    }

    protected void "align the 'org' group only"() {
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

    protected void "align the 'org' group to 2 different virtual platforms"() {
        buildFile << """
            dependencies {
                components.all(AlignOrgGroupTo2Platforms)
            }
            
            class AlignOrgGroupTo2Platforms implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        if ('org' == id.group) {
                           belongsTo("\${id.group}:platform:\${id.version}")
                           belongsTo("\${id.group}:platform2:\${id.version}")
                        }
                    }
                }
            }
        """
    }

    protected void 'a rule which declares that Groovy belongs to the Groovy and the Spring platforms'(boolean groovyVirtual=false, boolean springVirtual = false) {
        buildFile << """
            dependencies {
                components.all(GroovyRule)
            }
            
            class GroovyRule implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with {
                        if ('org.apache.groovy' == id.group) {
                           belongsTo("org.apache.groovy:platform:\${id.version}", $groovyVirtual)
                           belongsTo("org.springframework:spring-platform:1.0", $springVirtual)
                        }
                    }
                }
            }
        """
    }
}
