/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal

import spock.lang.Specification

class DefaultStartParameterTest extends Specification {

    def "prepares new build"() {
        def p = new DefaultStartParameter()
        p.longLivingProcess = true
        p.setBuildFile(new File("foo"))
        p.refreshDependencies = true

        when:
        def newBuild = p.newBuild()

        then:
        newBuild.longLivingProcess
        newBuild.buildFile == null
        newBuild.refreshDependencies
    }

    def "prepares new instance"() {
        def p = new DefaultStartParameter()
        p.longLivingProcess = false
        p.setBuildFile(new File("foo"))
        p.refreshDependencies = true

        when:
        def newInstance = p.newInstance()

        then:
        !newInstance.longLivingProcess
        newInstance.buildFile.name == 'foo'
        newInstance.refreshDependencies
    }
}
