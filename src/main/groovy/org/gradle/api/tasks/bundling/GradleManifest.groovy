/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.bundling

import java.util.jar.Attributes
import java.util.jar.Manifest
import org.gradle.api.tasks.AntBuilderAware

/**
 * @author Hans Dockter
 */
class GradleManifest implements AntBuilderAware {
    File file

    /**
     * The baseManifest is usually the common manifest info for all archives. It is usually not
     * manipulated via this object but its attributes are added to the Ant manifest generation.
     */
    Manifest baseManifest = new Manifest()

    Manifest manifest = new Manifest()

    GradleManifest() {}
    
    GradleManifest(Manifest baseManifest) {
        assert baseManifest
        this.baseManifest = baseManifest
    }

    GradleManifest mainAttributes(Map attributes) {
        attributes.each {String key, String value ->
            manifest.mainAttributes.putValue(key, value)
        }
        this
    }

    GradleManifest sections(Map attributes, String name) {
        Attributes manifestAttributes = manifest.entries[name]
        if (!manifestAttributes) {
            manifestAttributes = manifest.entries[name] = new Attributes()
        }
        attributes.each {String key, String value ->
            manifestAttributes.putValue(key, value)
        }
        this
    }

    public addToAntBuilder(node, String childNodeName = null) {
        node."${childNodeName ?: 'manifest'}"() {
            [manifest, baseManifest].each { manifest ->
                manifest.mainAttributes.keySet().each {Attributes.Name name ->
                    attribute(name: name.name, value: manifest.mainAttributes.getValue(name))
                }
            }
            [manifest, baseManifest].each { manifest ->
                manifest.entries.each {String sectionName, Attributes attributes ->
                    section(name: sectionName) {
                        attributes.keySet().each {Attributes.Name name ->
                            attribute(name: name.name, value: attributes.getValue(name))
                        }
                    }
                }
            }
        }
    }

}
