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

package org.gradle.integtests.samples.dependencymanagement

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.TextUtil.normaliseFileSeparators

class SamplesWorkingWithDependenciesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        executer.withRepositoryMirrors()
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/workingWithDependencies/iterateDependencies")
    @ToBeFixedForInstantExecution(iterationMatchers = ".*kotlin dsl")
    def "can iterate over dependencies assigned to a configuration with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds('iterateDeclaredDependencies')

        then:
        outputContains("""org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r
commons-codec:commons-codec:1.7""")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/workingWithDependencies/iterateArtifacts")
    def "can iterate over artifacts resolved for a module with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds('iterateResolvedArtifacts')

        then:
        def normalizedContent = normaliseFileSeparators(output)
        normalizedContent.contains('org.eclipse.jgit/org.eclipse.jgit/4.9.2.201712150930-r/a3a2d1df793245ebfc7322db3c2b9828ee184850/org.eclipse.jgit-4.9.2.201712150930-r.jar')
        normalizedContent.contains('org.apache.httpcomponents/httpclient/4.3.6/4c47155e3e6c9a41a28db36680b828ced53b8af4/httpclient-4.3.6.jar')
        normalizedContent.contains('commons-codec/commons-codec/1.7/9cd61d269c88f9fb0eb36cea1efcd596ab74772f/commons-codec-1.7.jar')
        normalizedContent.contains('com.jcraft/jsch/0.1.54/da3584329a263616e277e15462b387addd1b208d/jsch-0.1.54.jar')
        normalizedContent.contains('com.googlecode.javaewah/JavaEWAH/1.1.6/94ad16d728b374d65bd897625f3fbb3da223a2b6/JavaEWAH-1.1.6.jar')
        normalizedContent.contains('org.slf4j/slf4j-api/1.7.2/81d61b7f33ebeab314e07de0cc596f8e858d97/slf4j-api-1.7.2.jar')
        normalizedContent.contains('org.apache.httpcomponents/httpcore/4.3.3/f91b7a4aadc5cf486df6e4634748d7dd7a73f06d/httpcore-4.3.3.jar')
        normalizedContent.contains('commons-logging/commons-logging/1.1.3/f6f66e966c70a83ffbdb6f17a0919eaf7c8aca7f/commons-logging-1.1.3.jar')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/workingWithDependencies/walkGraph")
    @ToBeFixedForInstantExecution(because = "broken file collection")
    def "can walk the dependency graph of a configuration with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds('walkDependencyGraph')

        then:
        outputContains("""- org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r (requested)
     - com.jcraft:jsch:0.1.54 (requested)
     - com.googlecode.javaewah:JavaEWAH:1.1.6 (requested)
     - org.apache.httpcomponents:httpclient:4.3.6 (requested)
          - org.apache.httpcomponents:httpcore:4.3.3 (requested)
          - commons-logging:commons-logging:1.1.3 (requested)
          - commons-codec:commons-codec:1.7 (between versions 1.7 and 1.6)
     - org.slf4j:slf4j-api:1.7.2 (requested)
- commons-codec:commons-codec:1.7 (between versions 1.7 and 1.6)
- some:unresolved:2.5 (failed)""")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/workingWithDependencies/accessMetadataArtifact")
    @ToBeFixedForInstantExecution(iterationMatchers = ".*kotlin dsl")
    def "can accessing a module's metadata artifact with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds('printGuavaMetadata')

        then:
        output.contains("""guava-18.0.pom
Guava: Google Core Libraries for Java

    Guava is a suite of core and expanded libraries that include
    utility classes, google's collections, io classes, and much
    much more.

    Guava has only one code dependency - javax.annotation,
    per the JSR-305 spec.
""")

        where:
        dsl << ['groovy', 'kotlin']
    }
}
