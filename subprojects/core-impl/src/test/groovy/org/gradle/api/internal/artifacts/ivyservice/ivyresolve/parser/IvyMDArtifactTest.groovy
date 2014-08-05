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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.MDArtifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import spock.lang.Specification

class IvyMDArtifactTest extends Specification {

    def md = new DefaultModuleDescriptor(ModuleRevisionId.newInstance("org", "foo", "1.0"), "release", null)

    def "adds correct artifact to meta-data"() {
        def a = new IvyMDArtifact("foo", "jar", "ext", new File("foo.jar").toURI().toURL(), [a: 'b'])
        a.addConfiguration("runtime")
        md.addConfiguration(new Configuration("runtime"))

        when: a.addTo(md)

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.ext(jar)"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.ext(jar)"]
        IvyUtil.artifactsEqual(md.allArtifacts[0], new MDArtifact(md, "foo", "jar", "ext", new File("foo.jar").toURI().toURL(), [a: 'b']))
    }

    def "can be added to metadata that already contains artifacts"() {
        def a1 = new IvyMDArtifact(md, "foo", "jar", "jar").addConfiguration("runtime")
        def a2 = new IvyMDArtifact(md, "foo-all", "zip", "zip").addConfiguration("testUtil")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("testUtil"))

        when:
        a1.addTo(md)
        a2.addTo(md)

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.jar", "org#foo;1.0!foo-all.zip"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("testUtil")*.toString() == ["org#foo;1.0!foo-all.zip"]
    }

    def "can be added to metadata that already contains the same artifact in different configuration"() {
        def a1 = new IvyMDArtifact(md, "foo", "jar", "jar").addConfiguration("archives")
        //some publishers create ivy metadata that contains separate entries for the same artifact but different configurations
        //Gradle no longer does it TODO SF - integ test
        def a2 = new IvyMDArtifact(md, "foo", "jar", "jar").addConfiguration("runtime")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("archives"))

        when:
        a1.addTo(md)
        a2.addTo(md)

        then:
        md.allArtifacts*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("archives")*.toString() == ["org#foo;1.0!foo.jar"]
        md.getArtifacts("runtime")*.toString() == ["org#foo;1.0!foo.jar"]
        md.allArtifacts[0].configurations == ["archives", "runtime"]
    }
}
