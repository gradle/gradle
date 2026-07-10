/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs.dsl.links

import spock.lang.Specification

import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.dsl.source.model.TypeMetaData

class ClassLinkMetaDataTest extends Specification {

    public static final String SIMPLE_CLASSNAME = 'MyClass'
    public static final String METHOD_NAME = 'calculate'

    def "can define and look up overloaded methods"() {
        given:
        ClassMetaData classMetaData = new ClassMetaData("org.gradle.$SIMPLE_CLASSNAME")
        classMetaData.addMethod(METHOD_NAME, TypeMetaData.VOID, null)
        classMetaData.addMethod(METHOD_NAME, TypeMetaData.VOID, null).addParameter('param1', new TypeMetaData("java.lang.Integer"))
        def overloadedCalculateMethod = classMetaData.addMethod('calculate', TypeMetaData.VOID, null)
        overloadedCalculateMethod.addParameter('x', new TypeMetaData("java.lang.Integer"))
        overloadedCalculateMethod.addParameter('y', new TypeMetaData("java.lang.String"))
        ClassLinkMetaData classLinkMetaData = new ClassLinkMetaData(classMetaData)

        when:
        LinkMetaData linkMetaData = classLinkMetaData.getMethod("$METHOD_NAME()")

        then:
        linkMetaData != null
        linkMetaData.style == LinkMetaData.Style.Javadoc
        linkMetaData.displayName == "$SIMPLE_CLASSNAME.${METHOD_NAME}()"
        linkMetaData.urlFragment == "$METHOD_NAME--"

        when:
        linkMetaData = classLinkMetaData.getMethod("$METHOD_NAME(java.lang.Integer)")

        then:
        linkMetaData != null
        linkMetaData.style == LinkMetaData.Style.Javadoc
        linkMetaData.displayName == "$SIMPLE_CLASSNAME.${METHOD_NAME}(java.lang.Integer)"
        linkMetaData.urlFragment == "$METHOD_NAME-java.lang.Integer-"

        when:
        linkMetaData = classLinkMetaData.getMethod("$METHOD_NAME(java.lang.Integer, java.lang.String)")

        then:
        linkMetaData != null
        linkMetaData.style == LinkMetaData.Style.Javadoc
        linkMetaData.displayName == "$SIMPLE_CLASSNAME.${METHOD_NAME}(java.lang.Integer, java.lang.String)"
        linkMetaData.urlFragment == "$METHOD_NAME-java.lang.Integer, java.lang.String-"
    }
}
