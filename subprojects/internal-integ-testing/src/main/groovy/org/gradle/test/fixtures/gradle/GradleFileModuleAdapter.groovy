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

package org.gradle.test.fixtures.gradle

import groovy.json.JsonBuilder
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser

class GradleFileModuleAdapter {
    static boolean printComponentGAV = true

    private final String group
    private final String module
    private final String version
    private final String publishArtifactVersion
    private final List<VariantMetadataSpec> variants
    private final Map<String, String> attributes

    private final String buildId = "test-build"

    GradleFileModuleAdapter(String group, String module, String version, String publishArtifactVersion, List<VariantMetadataSpec> variants, Map<String, String> attributes = [:]) {
        this.group = group
        this.module = module
        this.version = version
        this.publishArtifactVersion = publishArtifactVersion
        this.variants = variants
        this.attributes = attributes
    }

    void publishTo(Writer writer, long publishId) {
        def jsonBuilder = new JsonBuilder()
        jsonBuilder {
            formatVersion GradleModuleMetadataParser.FORMAT_VERSION
            builtBy {
                gradle {
                    // padding is used to avoid test flakiness: often the FS will not correctly
                    // report lastModified, but file size would change so we would detect it!
                    padding "-" * publishId

                    // add a random build id to make sure file changes
                    buildId "$buildId"
                }
            }
            component {
                if (printComponentGAV) {
                    group this.group
                    module this.module
                    version this.version
                }
                attributes {
                    this.attributes.each { key, value ->
                        "$key" value
                    }
                }
            }
            variants(this.variants.collect { v ->
                { ->
                    if (v.name) {
                        name v.name
                    }
                    attributes {
                        v.attributes.each { key, value ->
                            "$key" value
                        }
                    }
                    files(v.artifacts.collect { a ->
                        { ->
                            if (a.name) {
                                name a.name
                            }
                            if (a.url) {
                                url a.url
                            }
                        }
                    })
                    dependencies(v.dependencies.collect { d ->
                        { ->
                            if (d.group) {
                                group d.group
                            }
                            if (d.module) {
                                module d.module
                            }
                            version {
                                if (d.strictVersion) {
                                    strictly d.strictVersion
                                } else if (d.version) {
                                    requires d.version
                                } else if (d.preferredVersion) {
                                    prefers d.preferredVersion
                                }
                                rejects d.rejects
                            }
                            if (d.endorseStrictVersions) {
                                endorseStrictVersions d.endorseStrictVersions
                            }
                            if (d.reason) {
                                reason d.reason
                            }
                            if (d.exclusions) {
                                excludes(d.exclusions.collect { e ->
                                    { ->
                                        if (e.group) {
                                            group e.group
                                        }
                                        if (e.module) {
                                            module e.module
                                        }
                                    }
                                })
                            }
                            if (d.attributes) {
                                attributes {
                                    d.attributes.each { key, value ->
                                        "$key" value
                                    }
                                }
                            }
                            if (d.requestedCapabilities) {
                                requestedCapabilities(d.requestedCapabilities.collect { c ->
                                    { ->
                                        if (c.group) {
                                            group c.group
                                        }
                                        if (c.name) {
                                            name c.name
                                        }
                                        if (c.version) {
                                            version c.version
                                        }
                                    }
                                })
                            }
                            if (d.artifactSelector) {
                                thirdPartyCompatibility {
                                    artifactSelector {
                                        name d.artifactSelector.name
                                        type d.artifactSelector.type
                                        if (d.artifactSelector.extension) {
                                            extension d.artifactSelector.extension
                                        }
                                        if (d.artifactSelector.classifier) {
                                            classifier d.artifactSelector.classifier
                                        }
                                    }
                                }
                            }
                        }
                    })
                    dependencyConstraints(v.dependencyConstraints.collect { dc ->
                        { ->
                            if (dc.group) {
                                group dc.group
                            }
                            if (dc.module) {
                                module dc.module
                            }
                            version {
                                if (dc.strictVersion) {
                                    strictly dc.strictVersion
                                } else if (dc.version) {
                                    requires dc.version
                                } else if (dc.preferredVersion) {
                                    prefers dc.preferredVersion
                                }
                                if (dc.rejects) {
                                    rejects dc.rejects
                                }
                            }
                            if (dc.reason) {
                                reason dc.reason
                            }
                            if (dc.attributes) {
                                attributes {
                                    dc.attributes.each { key, value ->
                                        "$key" value
                                    }
                                }
                            }
                        }
                    })
                    capabilities(v.capabilities.collect { c ->
                        { ->
                            if (c.group) {
                                group c.group
                            }
                            if (c.name) {
                                name c.name
                            }
                            if (c.version) {
                                version c.version
                            }
                        }
                    })
                    if (v.availableAt) {
                        'available-at' {
                            if (v.availableAt.url) {
                                url v.availableAt.url
                            }
                            if (v.availableAt.group) {
                                group v.availableAt.group
                            }
                            if (v.availableAt.module) {
                                module v.availableAt.module
                            }
                            if (v.availableAt.version) {
                                version v.availableAt.version
                            }
                        }
                    }
                }
            })
        }
        jsonBuilder.writeTo(writer)
    }

}
