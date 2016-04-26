/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Clock
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({ GradleContextualExecuter.isParallel() })
class AntArchiveIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
apply plugin: 'base'

repositories {
    jcenter()
}

configurations {
    dep
}

dependencies {
    // just so we're zipping up a bunch of stuff
    dep 'org.springframework:spring-core:2.5'
    dep 'org.springframework:spring-aop:2.5'
    dep 'org.apache.httpcomponents:httpclient:4.3.6'
}

task copySrc(type: Copy) {
    ext.outputDir = new File(buildDir, "src")
    into(outputDir)
    from(configurations.dep) {
        into "lib"
    }
    from(configurations.dep) {
        into "lib2"
    }
    from({ configurations.dep.collect { zipTree(it) } }) {
        into "extract"
    }
    from({ configurations.dep.collect { zipTree(it) } }) {
        into "extract2"
    }
}

task antTask() {
    ext.tarFile = new File(buildDir, "distributions/\${name}.tar")
    doLast {
        ant.tar(destfile: tarFile, basedir: copySrc.outputDir)
    }
}

task gradleTask(type: Tar) {
    from(copySrc.outputDir)
}

def testTasks = tasks.matching { it.name.endsWith("Task") }

afterEvaluate {
    configure(testTasks) {
        dependsOn copySrc
        ext.clock = new ${Clock.canonicalName}()
        ext.elapsed = 0
        doFirst {
            clock.reset()
        }
        doLast {
            elapsed = clock.timeInMs
        }
    }
}

boolean closeEnough(gradleTime, antTime) {
    def delta = gradleTime - antTime
    logger.warn("Gradle {}, Ant {}, delta {}", gradleTime, antTime, delta)
    delta < 5000
}

task assertGradleNotSlowerThanAnt() {
    dependsOn testTasks

    doLast {
        assert closeEnough(gradleTask.elapsed, antTask.elapsed)
    }
}
"""
    }

    @Unroll
    def "gradle is not slower than ant for #compression"() {
        given:
        buildFile << """
gradleTask {
    ${gradle}
}
antTask {
    doLast {
        ${ant}
    }
}
"""
        expect:
        succeeds("assertGradleNotSlowerThanAnt")
        where:
        compression      | gradle                            | ant
        "BZIP2"          | "compression = Compression.BZIP2" | "ant.bzip2(src: tarFile, destfile: new File(buildDir, 'distributions/ant.tar.bz2'))"
        "No compression" | "// no compression"               | "// no extra work"
        "GZIP"           | "compression = Compression.GZIP"  | "ant.gzip(src: tarFile, destfile: new File(buildDir, 'distributions/ant.tar.gz'))"
    }

}
