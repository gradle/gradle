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

package org.gradle.scala.scaladoc

import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.scala.ScalaCompilationFixture


class ScalaDocIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    String scaladoc = ":${ScalaPlugin.SCALA_DOC_TASK_NAME}"
    ScalaCompilationFixture classes = new ScalaCompilationFixture(testDirectory)

    def "changing the Scala version makes Scaladoc out of date"() {
        classes.baseline()
        buildScript(classes.buildScript())
        def newScalaVersion = '2.11.12'

        when:
        succeeds scaladoc
        then:
        executedAndNotSkipped scaladoc

        when:
        succeeds scaladoc
        then:
        skipped scaladoc
        newScalaVersion != this.classes.scalaVersion

        when:
        this.classes.scalaVersion = newScalaVersion
        buildScript(this.classes.buildScript())
        succeeds scaladoc
        then:
        executedAndNotSkipped scaladoc
    }

    def "scaladoc is loaded from cache"() {
        classes.baseline()
        buildScript(classes.buildScript())

        when:
        withBuildCache().run scaladoc

        then:
        executedAndNotSkipped scaladoc

        when:
        succeeds 'clean'
        withBuildCache().run scaladoc

        then:
        skipped scaladoc
    }

}
