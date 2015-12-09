/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.component.external.model

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Specification

import static com.google.common.collect.Sets.newHashSet

class BuildableIvyModuleResolveMetaDataTest extends Specification {

    def md = new DefaultModuleDescriptor(ModuleRevisionId.newInstance("org", "foo", "1.0"), "release", null)
    def meta = new BuildableIvyModuleResolveMetadata(md)

    def "adds correct artifact to metadata"() {
        def a = ivyArtifact("foo", "jar", "ext", [a: 'b'])
        md.addConfiguration(new Configuration("runtime"))

        when: meta.addArtifact(a, newHashSet("runtime"))

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.ext(jar)"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.ext(jar)"]
    }

    private static IvyArtifactName ivyArtifact(String name, String type, String ext, Map<String, String> attributes = [:]) {
        return new DefaultIvyArtifactName(name, type, ext, attributes)
    }

    def "prevents adding artifact without configurations"() {
        def unattached = ivyArtifact("foo", "jar", "ext", [a: 'b'])
        md.addConfiguration(new Configuration("runtime"))

        when: meta.addArtifact(unattached, newHashSet())

        then: thrown(IllegalArgumentException)
    }

    def "can be added to metadata that already contains artifacts"() {
        def a1 = ivyArtifact("foo", "jar", "jar")
        def a2 = ivyArtifact("foo-all", "zip", "zip")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("testUtil"))

        when:
        meta.addArtifact(a1, newHashSet("runtime"))
        meta.addArtifact(a2, newHashSet("testUtil"))

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.jar", "org#foo;1.0!foo-all.zip"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("testUtil")*.toString() == ["org#foo;1.0!foo-all.zip"]
    }

    def "can be added to metadata that already contains the same artifact in different configuration"() {
        def a1 = ivyArtifact("foo", "jar", "jar")
        //some publishers create ivy metadata that contains separate entries for the same artifact but different configurations
        def a2 = ivyArtifact("foo", "jar", "jar")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("archives"))

        when:
        meta.addArtifact(a1, newHashSet("archives"))
        meta.addArtifact(a2, newHashSet("runtime"))

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("archives")*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.jar"]
        md.allArtifacts[0].configurations == ["archives", "runtime"]
    }
}
