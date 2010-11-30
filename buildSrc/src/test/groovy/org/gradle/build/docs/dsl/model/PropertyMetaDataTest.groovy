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
package org.gradle.build.docs.dsl.model

import spock.lang.Specification

class PropertyMetaDataTest extends Specification {
    final ClassMetaData classMetaData = Mock()
    final PropertyMetaData propertyMetaData = new PropertyMetaData('prop', classMetaData)

    def locatesInheritedCommentInSuperClass() {
        ClassMetaData superClassMetaData = Mock()
        PropertyMetaData overriddenProperty = Mock()

        when:
        def p = propertyMetaData.overriddenProperty

        then:
        _ * classMetaData.superClass >> superClassMetaData
        _ * classMetaData.interfaces >> []
        _ * superClassMetaData.findDeclaredProperty('prop') >> overriddenProperty
        p == overriddenProperty
    }

    def locatesInheritedCommentInInterface() {
        ClassMetaData interfaceMetaData = Mock()
        PropertyMetaData overriddenProperty = Mock()

        when:
        def p = propertyMetaData.overriddenProperty

        then:
        _ * classMetaData.superClass >> null
        _ * classMetaData.interfaces >> [interfaceMetaData]
        _ * interfaceMetaData.findDeclaredProperty('prop') >> overriddenProperty
        p == overriddenProperty
    }

    def locatesInheritedCommentInAncestorClass() {
        ClassMetaData superClassMetaData = Mock()
        ClassMetaData ancestorClassMetaData = Mock()
        PropertyMetaData overriddenProperty = Mock()

        when:
        def p = propertyMetaData.overriddenProperty

        then:
        _ * classMetaData.superClass >> superClassMetaData
        _ * classMetaData.interfaces >> []
        _ * superClassMetaData.findDeclaredProperty('prop') >> null
        _ * superClassMetaData.superClass >> ancestorClassMetaData
        _ * superClassMetaData.interfaces >> []
        _ * ancestorClassMetaData.findDeclaredProperty('prop') >> overriddenProperty
        p == overriddenProperty
    }

    def locatesInheritedCommentInInterfaceOfAncestorClass() {
        ClassMetaData superClassMetaData = Mock()
        ClassMetaData interfaceMetaData = Mock()
        PropertyMetaData overriddenProperty = Mock()

        when:
        def p = propertyMetaData.overriddenProperty

        then:
        _ * classMetaData.superClass >> superClassMetaData
        _ * classMetaData.interfaces >> []
        _ * superClassMetaData.findDeclaredProperty('prop') >> null
        _ * superClassMetaData.superClass >> null
        _ * superClassMetaData.interfaces >> [interfaceMetaData]
        _ * interfaceMetaData.findDeclaredProperty('prop') >> overriddenProperty
        p == overriddenProperty
    }

    def hasEmptyInheritedCommentWhenNoSuperClass() {
        when:
        def p = propertyMetaData.overriddenProperty

        then:
        _ * classMetaData.superClass >> null
        _ * classMetaData.interfaces >> []
        p == null
    }
    
    def hasEmptyInheritedCommentWhenPropertyDoesNotOverridePropertyInSuperClass() {
        ClassMetaData superClassMetaData = Mock()

        when:
        def p = propertyMetaData.overriddenProperty

        then:
        _ * classMetaData.superClass >> superClassMetaData
        _ * classMetaData.interfaces >> []
        _ * superClassMetaData.findDeclaredProperty('prop') >> null
        _ * superClassMetaData.superClass >> null
        _ * superClassMetaData.interfaces >> []
        p == null
    }
}
