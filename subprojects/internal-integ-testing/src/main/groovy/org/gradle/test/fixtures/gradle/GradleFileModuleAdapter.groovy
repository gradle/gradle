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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser
import org.gradle.test.fixtures.file.TestFile

class GradleFileModuleAdapter {
    private final String group
    private final String module
    private final String version
    private final List<VariantMetadataSpec> variants
    private final Map<String, String> attributes

    GradleFileModuleAdapter(String group, String module, String version, List<VariantMetadataSpec> variants, Map<String, String> attributes = [:]) {
        this.group = group
        this.module = module
        this.version = version
        this.variants = variants
        this.attributes = attributes
    }

    void publishTo(TestFile moduleDir) {
        moduleDir.createDir()
        def file = moduleDir.file("$module-${version}.module")
        def jsonBuilder = new JsonBuilder()
        jsonBuilder {
            formatVersion ModuleMetadataParser.FORMAT_VERSION
            builtBy {
                gradle { }
            }
            component {
                group this.group
                module this.module
                version this.version
                attributes {
                    this.attributes.each { key, value ->
                        "$key" value
                    }
                }
            }
            variants(this.variants.collect { v ->
                { ->
                    name v.name
                    attributes {
                        v.attributes.each { key, value ->
                            "$key" value
                        }
                    }
                    files(v.artifacts.collect { a ->
                        { ->
                            name a.name
                            url a.name
                        }
                    })
                    dependencies(v.dependencies.collect { d ->
                        { ->
                            group d.group
                            module d.module
                            version {
                                prefers d.prefers
                                if (d.strictVersion) {
                                    strictly d.strictVersion
                                }
                                rejects d.rejects
                            }
                            if (d.reason) {
                                reason d.reason
                            }
                            if (d.exclusions) {
                                excludes(d.exclusions.collect { e ->
                                    { ->
                                        group e.group
                                        module e.module
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
                        }
                    })
                    dependencyConstraints(v.dependencyConstraints.collect { dc ->
                        { ->
                            group dc.group
                            module dc.module
                            version {
                                if (dc.prefers) {
                                    prefers dc.prefers
                                }
                                if (dc.strictVersion) {
                                    strictly dc.strictVersion
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
                            group c.group
                            name c.name
                            version c.version
                        }
                    })
                }
            })
        }
        file.withWriter('utf-8') {
            jsonBuilder.writeTo(it)
        }
    }

}
