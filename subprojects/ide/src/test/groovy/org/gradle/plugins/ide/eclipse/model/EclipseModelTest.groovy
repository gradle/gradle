/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model

import spock.lang.Specification

class EclipseModelTest extends Specification {

    EclipseModel model = new EclipseModel(classpath: new EclipseClasspath(null))

    def "enables setting path variables even if wtp is not configured"() {
        given:
        model.wtp = null

        when:
        model.pathVariables(one: new File('.'))
        model.pathVariables(two: new File('.'))

        then:
        model.classpath.pathVariables == [one: new File('.'), two: new File('.')]
    }
    
    def "enables setting path variables even if wtp component is not configured"() {
        given:
        model.wtp = new EclipseWtp(model.classpath)
        //for example when wtp+java applied but project is not a dependency to any war/ear.
        assert model.wtp.component == null

        when:
        model.pathVariables(one: new File('.'))

        then:
        model.classpath.pathVariables == [one: new File('.')]
    }

    def "enables setting path variables"() {
        given:
        model.wtp = new EclipseWtp(model.classpath)
        model.wtp.component = new EclipseWtpComponent(null, null)
        
        when:
        model.pathVariables(one: new File('.'))

        then:
        model.classpath.pathVariables == [one: new File('.')]
        model.wtp.component.pathVariables == [one: new File('.')]
    }
}
