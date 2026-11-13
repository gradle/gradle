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
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.internal.DetectionContext
import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.api.problems.internal.location.CompiledCodeFrame
import org.gradle.api.problems.internal.location.CompiledCodePosition
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
        frames*.displayName == [
            "property bar",
            "class com.acme.AcmeTask",
            "task :app:acme",
            "plugin com.acme.foo",
            "plugin com.acme.convention",
            new File("/work/build.gradle").absolutePath
        ]
        frames*.class == [
            PropertyFrame,
            ClassFrame,
            TaskFrame,
            PluginClassFrame,
            PluginClassFrame,
            FileLocationFrame
        ]
        frames*.locality == [
            Locality.UNKNOWN,
            Locality.UNKNOWN,
            Locality.EXTERNAL,
            Locality.EXTERNAL,
            Locality.LOCAL,
            Locality.LOCAL
        ]
        (frames[5].position as FilePosition).line == null
    }

    def "a validation problem of another type than the task's gets no lineage"() {
        given:
        def task = new TaskIdentity(":plugin:validatePlugins", ":plugin", ":", "org.gradle.plugin.devel.tasks.ValidatePlugins", null)
        def validation = new Trace([new PropertyFrame("bar", Locality.UNKNOWN), new ClassFrame("com.acme.AcmeTask", Locality.UNKNOWN)])

        expect:
        OriginTraces.withLineage([validation], task)[0].frames*.displayName == [
            "property bar",
            "class com.acme.AcmeTask"
        ]
    }

    def "a file location leaf and a problem outside a task get no lineage"() {
        given:
        def file = new Trace([new FileLocationFrame(new FilePosition("/work/build.gradle", 3, null, null, null), Locality.LOCAL)])
        def task = new TaskIdentity(":app:acme", ":app", ":", "com.acme.AcmeTask", null)

        expect:
        OriginTraces.withLineage([file], task)[0].frames*.displayName == ["/work/build.gradle:3"]
        OriginTraces.withLineage([file], null)[0].frames*.displayName == ["/work/build.gradle:3"]
    }

    def "a task the reporter named gets its own lineage, whichever task is running"() {
        given:
        def script = new UserCodeSource.Script(Describables.of("build file 'build.gradle'"), new File("/work/build.gradle").toURI(), Locality.LOCAL)
        def plugin = new UserCodeSource.Binary(Describables.of("plugin 'com.acme.foo'"), "com.acme.FooPlugin", "com.acme.foo", Locality.EXTERNAL, script)
        def named = new Trace([new PropertyFrame("output property", "outputDir", Locality.EXTERNAL), new org.gradle.api.problems.internal.location.TaskFrame(":app:generate", plugin)])
        def running = new TaskIdentity(":app:report", ":app", ":", "com.acme.ReportTask", null)

        expect:
        OriginTraces.withLineage([named], running)[0].frames*.displayName == [
            "output property outputDir",
            "task :app:generate",
            "plugin com.acme.foo",
            new File("/work/build.gradle").absolutePath
        ]
        OriginTraces.withLineage([named], null)[0].frames.size() == 4

        and: "a task frame without a registration record stays as it is"
        def bare = new Trace([new org.gradle.api.problems.internal.location.TaskFrame(":app:generate", Locality.UNKNOWN)])
        OriginTraces.withLineage([bare], running)[0].frames*.displayName == ["task :app:generate"]
    }

    def "a task type that is a nested class matches the dotted name validation gives it"() {
        given:
        def task = new TaskIdentity(":acme", ":", ":", 'com.acme.Tasks$AcmeTask', null)
        def validation = new Trace([new PropertyFrame("bar", Locality.UNKNOWN), new ClassFrame("com.acme.Tasks.AcmeTask", Locality.UNKNOWN)])

        expect:
        OriginTraces.withLineage([validation], task)[0].frames*.displayName == [
            "property bar",
            "class com.acme.Tasks.AcmeTask",
            "task :acme"
        ]
    }

    def "a library line gives way to the first line below it from the jar of the plugin being applied"() {
        given:
        def plugin = pluginIn("/repo/acme.jar", Locality.EXTERNAL)

        when:
        def traces = OriginTraces.withUserCodeCallers([compiledLeaf("com.google.Util", 10)], [stack(null, "com.google.Util:10", "com.acme.FooPlugin:42", "com.acme.FooPlugin:7")], new DetectionContext(null, null, plugin), classesOf("/repo/acme.jar", "com.acme.FooPlugin"))

        then:
        traces[0].frames*.displayName == ["FooPlugin.java:42"]
        traces[0].frames[0].locality == Locality.EXTERNAL
    }

    def "a leaf that is already in the plugin's jar stays and takes the plugin's locality"() {
        given:
        def plugin = pluginIn("/repo/acme.jar", Locality.LOCAL)

        when:
        def traces = OriginTraces.withUserCodeCallers([compiledLeaf("com.acme.FooPlugin", 42)], [stack(null, "com.acme.FooPlugin:42")], new DetectionContext(null, null, plugin), classesOf("/repo/acme.jar", "com.acme.FooPlugin"))

        then:
        traces[0].frames*.displayName == ["FooPlugin.java:42"]
        traces[0].frames[0].locality == Locality.LOCAL
    }

    def "the plugin that registered the running task counts, and the code that applied it"() {
        given:
        def convention = new UserCodeSource.Binary(Describables.of("plugin"), "com.acme.ConventionPlugin", null, Locality.LOCAL, null, new File("/build/convention.jar"))
        def plugin = new UserCodeSource.Binary(Describables.of("plugin"), "com.acme.FooPlugin", "com.acme.foo", Locality.EXTERNAL, convention, new File("/repo/acme.jar"))
        def detection = new DetectionContext(new TaskIdentity(":app:acme", ":app", ":", "com.acme.AcmeTask", plugin), null, null)

        when:
        def traces = OriginTraces.withUserCodeCallers([compiledLeaf("com.google.Util", 10)], [stack(null, "com.google.Util:10", "com.acme.ConventionPlugin:5")], detection, classesOf("/build/convention.jar", "com.acme.ConventionPlugin"))

        then:
        traces[0].frames*.displayName == ["ConventionPlugin.java:5"]
        traces[0].frames[0].locality == Locality.LOCAL
    }

    def "a script line nearer than the plugin becomes the leaf"() {
        given:
        def plugin = pluginIn("/repo/acme.jar", Locality.EXTERNAL)
        def scriptLine = Stub(LineInFileLocation) {
            getPath() >> "/work/shared.gradle"
            getLine() >> 3
            getColumn() >> -1
            getLength() >> -1
        }

        when:
        def traces = OriginTraces.withUserCodeCallers([compiledLeaf("com.google.Util", 10)], [stack(scriptLine, "com.google.Util:10", "shared_abc:3@shared.gradle", "com.acme.FooPlugin:42")], new DetectionContext(null, null, plugin), classesOf("/repo/acme.jar", "com.acme.FooPlugin"))

        then:
        traces[0].frames*.displayName == ["/work/shared.gradle:3"]
        traces[0].frames[0] instanceof FileLocationFrame
    }

    def "a script line becomes the leaf also when no plugin is running"() {
        given:
        def scriptLine = Stub(LineInFileLocation) {
            getPath() >> "/work/build.gradle"
            getLine() >> 5
            getColumn() >> -1
            getLength() >> -1
        }

        expect:
        OriginTraces.withUserCodeCallers([compiledLeaf("com.google.Util", 10)], [stack(scriptLine, "com.google.Util:10", "build_abc:5@build.gradle")], DetectionContext.NONE, classesOf("/repo/acme.jar"))[0].frames*.displayName == ["/work/build.gradle:5"]
    }

    def "without a script line or a frame from a counted plugin's jar, nothing changes"() {
        given:
        def original = [compiledLeaf("com.google.Util", 10)]
        def locations = [stack(null, "com.google.Util:10", "com.other.Thing:1")]

        expect:
        OriginTraces.withUserCodeCallers(original, locations, DetectionContext.NONE, classesOf("/repo/acme.jar", "com.acme.FooPlugin")).is(original)
        OriginTraces.withUserCodeCallers(original, locations, new DetectionContext(null, null, pluginIn("/repo/acme.jar", Locality.EXTERNAL)), classesOf("/repo/acme.jar", "com.acme.FooPlugin")).is(original)
    }

    private static UserCodeSource.Binary pluginIn(String jar, Locality locality) {
        new UserCodeSource.Binary(Describables.of("plugin"), "com.acme.FooPlugin", "com.acme.foo", locality, null, new File(jar))
    }

    private static Trace compiledLeaf(String className, int line) {
        new Trace([new CompiledCodeFrame(new CompiledCodePosition(className.substring(className.lastIndexOf('.') + 1) + ".java", className, line), Locality.UNKNOWN)])
    }

    /**
     * A stack of "class:line" elements, top first; "class:line@file" names the source file.
     */
    private StackTraceLocation stack(LineInFileLocation scriptLine, String... elements) {
        def stackTrace = elements.collect {
            def (frame, fileName) = it.contains("@") ? it.split("@") : [it, null]
            def (className, line) = frame.split(":")
            new StackTraceElement(className, "run", fileName ?: className.substring(className.lastIndexOf('.') + 1) + ".java", line as int)
        }
        Stub(StackTraceLocation) {
            getFileLocation() >> scriptLine
            getStackTrace() >> stackTrace
        }
    }

    private ClassFileLookup classesOf(String jar, String... classNames) {
        Stub(ClassFileLookup) {
            contains(new File(jar), _) >> { File location, String className -> className in classNames }
        }
    }
}
