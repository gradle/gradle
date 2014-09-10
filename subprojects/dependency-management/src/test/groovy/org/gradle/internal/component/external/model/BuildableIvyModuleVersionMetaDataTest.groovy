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
import spock.lang.Specification

class BuildableIvyModuleVersionMetaDataTest extends Specification {

    def md = new DefaultModuleDescriptor(ModuleRevisionId.newInstance("org", "foo", "1.0"), "release", null)
    def meta = new BuildableIvyModuleVersionMetaData(md)

    def "adds correct artifact to meta-data"() {
        def a = new BuildableIvyArtifact("foo", "jar", "ext", new File("foo.jar").toURI().toURL(), [a: 'b'])
        a.addConfiguration("runtime")
        md.addConfiguration(new Configuration("runtime"))

        when: meta.addArtifact(a)

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.ext(jar)"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.ext(jar)"]
    }

    def "prevents adding artifact without configurations"() {
        def unattached = new BuildableIvyArtifact("foo", "jar", "ext", new File("foo.jar").toURI().toURL(), [a: 'b'])
        md.addConfiguration(new Configuration("runtime"))

        when: meta.addArtifact(unattached)

        then: thrown(IllegalArgumentException)
    }

    def "can be added to metadata that already contains artifacts"() {
        def a1 = new BuildableIvyArtifact("foo", "jar", "jar").addConfiguration("runtime")
        def a2 = new BuildableIvyArtifact("foo-all", "zip", "zip").addConfiguration("testUtil")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("testUtil"))

        when:
        meta.addArtifact(a1)
        meta.addArtifact(a2)

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.jar", "org#foo;1.0!foo-all.zip"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("testUtil")*.toString() == ["org#foo;1.0!foo-all.zip"]
    }

    def "can be added to metadata that already contains the same artifact in different configuration"() {
        def a1 = new BuildableIvyArtifact("foo", "jar", "jar").addConfiguration("archives")
        //some publishers create ivy metadata that contains separate entries for the same artifact but different configurations
        def a2 = new BuildableIvyArtifact("foo", "jar", "jar").addConfiguration("runtime")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("archives"))

        when:
        meta.addArtifact(a1)
        meta.addArtifact(a2)

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("archives")*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.jar"]
        md.allArtifacts[0].configurations == ["archives", "runtime"]
    }
}
