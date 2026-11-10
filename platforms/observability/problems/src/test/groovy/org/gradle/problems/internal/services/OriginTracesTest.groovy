/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.problems.internal.services

import org.gradle.api.problems.internal.TaskIdentity
import org.gradle.api.problems.internal.location.ClassFrame
import org.gradle.api.problems.internal.location.FileLocationFrame
import org.gradle.api.problems.internal.location.FilePosition
import org.gradle.api.problems.internal.location.PluginClassFrame
import org.gradle.api.problems.internal.location.PropertyFrame
import org.gradle.api.problems.internal.location.TaskFrame
import org.gradle.api.problems.internal.location.Trace
import org.gradle.internal.Describables
import org.gradle.internal.code.Locality
import org.gradle.internal.code.UserCodeSource
import spock.lang.Specification

class OriginTracesTest extends Specification {

    def "a validation problem of the running task's type gets the task and the code that registered and applied it as lineage"() {
        given:
        def script = new UserCodeSource.Script(Describables.of("build file 'build.gradle'"), new File("/work/build.gradle").toURI(), Locality.LOCAL)
        def convention = new UserCodeSource.Binary(Describables.of("plugin 'com.acme.convention'"), "com.acme.ConventionPlugin", "com.acme.convention", Locality.LOCAL, script)
        def plugin = new UserCodeSource.Binary(Describables.of("plugin 'com.acme.foo'"), "com.acme.FooPlugin", "com.acme.foo", Locality.EXTERNAL, convention)
        def task = new TaskIdentity(":app:acme", ":app", ":", "com.acme.AcmeTask", plugin)
        def validation = new Trace([new PropertyFrame("bar", Locality.UNKNOWN), new ClassFrame("com.acme.AcmeTask", Locality.UNKNOWN)])

        when:
        def frames = OriginTraces.withLineage([validation], task)[0].frames

        then:
        frames*.displayName == ["property bar", "class com.acme.AcmeTask", "task :app:acme", "plugin com.acme.foo", "plugin com.acme.convention", new File("/work/build.gradle").absolutePath]
        frames*.class == [PropertyFrame, ClassFrame, TaskFrame, PluginClassFrame, PluginClassFrame, FileLocationFrame]
        frames*.locality == [Locality.UNKNOWN, Locality.UNKNOWN, Locality.EXTERNAL, Locality.EXTERNAL, Locality.LOCAL, Locality.LOCAL]
        (frames[5].position as FilePosition).line == null
    }

    def "a validation problem of another type than the task's gets no lineage"() {
        given:
        def task = new TaskIdentity(":plugin:validatePlugins", ":plugin", ":", "org.gradle.plugin.devel.tasks.ValidatePlugins", null)
        def validation = new Trace([new PropertyFrame("bar", Locality.UNKNOWN), new ClassFrame("com.acme.AcmeTask", Locality.UNKNOWN)])

        expect:
        OriginTraces.withLineage([validation], task)[0].frames*.displayName == ["property bar", "class com.acme.AcmeTask"]
    }

    def "a file location leaf and a problem outside a task get no lineage"() {
        given:
        def file = new Trace([new FileLocationFrame(new FilePosition("/work/build.gradle", 3, null, null, null), Locality.LOCAL)])
        def task = new TaskIdentity(":app:acme", ":app", ":", "com.acme.AcmeTask", null)

        expect:
        OriginTraces.withLineage([file], task)[0].frames*.displayName == ["/work/build.gradle:3"]
        OriginTraces.withLineage([file], null)[0].frames*.displayName == ["/work/build.gradle:3"]
    }
}
