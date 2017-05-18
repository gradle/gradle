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

package org.gradle.groovy.scripts

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class StaticCompilationTest extends AbstractIntegrationSpec {
    def "static compilation uses information provided @DelegatesTo and @ClosureArgs in Project"() {
        /*
        This test contains many (seemingly meaningless) assignments such as
        'Foo x = delegate'.  These serve two purposes.
        1) If, in the above example, the type 'Foo' did not match the type 'delegate',
           Groovy's static compiler would reject it as invalid.  This verifies the type
           information provided by closures annotated with @DelegatesTo and @ClosureArgs
           is being used by static compilation.
        2) If, in the above example, 'it' was not _actually_ of type 'Foo', Groovy's run-
           time would reject it at execution time as invalid.  This helps verify the type
           information provided by closures annotated with @DelegatesTo and @ClosureArgs
           is actually _correct_.
        */

        buildFile << """
@groovy.transform.CompileStatic
void setup(Project p) {
    p.task("foo", type: DefaultTask) {
        Task a = it
        Task b = delegate
        assert name == 'foo'
    }
    p.task("bar") {
        Task a = it
        Task b = delegate
        assert name == 'bar'
    }
    p.project(":") {
        Project a = it
        Project b = delegate
        assert path == ':'
    }
    p.files("build.gradle") {
        ConfigurableFileCollection a = it
        ConfigurableFileCollection b = delegate
        assert getBuiltBy().empty
    }
    p.fileTree(".") {
        ConfigurableFileTree a = it
        ConfigurableFileTree b = delegate
        assert getBuiltBy().empty
    }
    try {
        p.javaexec {
            // This block will run, but the execution will fail.
            // That's okay - we're just using this for type checking.
            // (A GroovyRuntimeException due to type-checking would
            // be thrown here if we got the types wrong.)
            JavaExecSpec a = it
            JavaExecSpec b = delegate
            args(['foo', 'bar'])     
        }
    } catch (IllegalStateException ex) {
        // We don't have any Java to execute.
    }
    // Same as above, but for exec
    try {
        p.exec {
            ExecSpec a = it
            ExecSpec b = delegate
            args(['foo', 'bar'])
        }
    } catch (IllegalStateException ex) { }
    p.ant {
        AntBuilder a = it
        AntBuilder b = delegate
        assert getLifecycleLogLevel() == null
    }
    p.configurations {
        ConfigurationContainer a = it
        ConfigurationContainer b = delegate
        create('foo') {
        }
    }
    p.artifacts {
        ArtifactHandler a = it
        ArtifactHandler b = delegate
        add('foo', file('foo.jar'))
    }
    p.subprojects {
        Project a = it
        Project b = delegate
        beforeEvaluate {
            Project c = it
        }
    }
    p.buildscript {
        ScriptHandler a = it
        ScriptHandler b = delegate
        assert sourceURI != null
    }
    p.allprojects {
        Project a = it
        Project b = delegate
        assert name != null
    }
    p.afterEvaluate {
        Project a = it
    }
    p.repositories {
        RepositoryHandler a = it
        RepositoryHandler b = delegate
        flatDir name: 'libs', dirs: "libs"
    }
    p.dependencies {
        DependencyHandler a = it
        DependencyHandler b = delegate
        add('foo', 'com:foo:1.0')
    }
    p.copy {
        from "build.gradle"
        into "foo.txt" // doesn't matter, but we have to provide something.
        CopySpec a = it
        CopySpec b = delegate
    }
    p.copySpec {
        CopySpec a = it
        CopySpec b = delegate
        from "build.gradle"
    }
    NamedDomainObjectContainer container = p.container(HashMap.class) {
        String a = it
        [name: it, cool: 'very']
    }
    container.create("foo") // Ensure container closure called so we check types.
}
setup(project)
        """

        settingsFile << """
// Ensures 'subprojects' closure above is invoked
include 'fauxSubproject'
        """

        expect:
        succeeds("foo", "bar")
    }
}
