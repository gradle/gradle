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

package org.gradle.internal.reflect

import spock.lang.Specification
import spock.lang.Unroll

import java.beans.Introspector

class PropertyAccessorTypeTest extends Specification {
    @Unroll
    def "method names #getGetterName, #isGetterName and #setterName extract to property name '#propertyName' following the JavaBeans spec"() {
        expect:
        PropertyAccessorType.isGetGetterName(getGetterName)
        PropertyAccessorType.GET_GETTER.propertyNameFor(getGetterName) == propertyName
        Introspector.decapitalize(getGetterName.substring(3)) == propertyName

        PropertyAccessorType.isIsGetterName(isGetterName)
        PropertyAccessorType.IS_GETTER.propertyNameFor(isGetterName) == propertyName
        Introspector.decapitalize(isGetterName.substring(2)) == propertyName

        PropertyAccessorType.isSetterName(setterName)
        PropertyAccessorType.SETTER.propertyNameFor(setterName) == propertyName
        Introspector.decapitalize(setterName.substring(3)) == propertyName

        where:
        getGetterName    | isGetterName    | setterName       | propertyName
        "getUrl"         | "isUrl"         | "setUrl"         | "url"
        "getURL"         | "isURL"         | "setURL"         | "URL"
        "getcCompiler"   | "iscCompiler"   | "setcCompiler"   | "cCompiler"
        "getCCompiler"   | "isCCompiler"   | "setCCompiler"   | "CCompiler"
        "getCppCompiler" | "isCppCompiler" | "setCppCompiler" | "cppCompiler"
        "getCPPCompiler" | "isCPPCompiler" | "setCPPCompiler" | "CPPCompiler"
        "getA"           | "isA"           | "setA"           | "a"
        "getb"           | "isb"           | "setb"           | "b"
    }

    static class Bean {
        private String myurl, myURL, mycCompiler, myCCompilerField, mycppCompilerField, myCPPCompilerField, mya, myb
        String getUrl() { myurl }
        void setUrl(String value) { myurl = value }
        String getURL() { myURL }
        void setURL(String value) { myURL = value }
        String getcCompiler() { mycCompiler }
        void setcCompiler(String value) { mycCompiler = value }
        String getCCompiler() { myCCompilerField }
        void setCCompiler(String value) { myCCompilerField = value }
        String getCppCompiler() { mycppCompilerField }
        void setCppCompiler(String value) { mycppCompilerField = value }
        String getCPPCompiler() { myCPPCompilerField }
        void setCPPCompiler(String value) { myCPPCompilerField = value }
        String getA() { mya }
        void setA(String value) { mya = value }
        String getb() { myb }
        void setb(String value) { myb = value }
    }

    def "property extraction is on par with groovy properties"() {
        given:
        def bean = new Bean()

        when:
        // Exercise setters
        bean.url = 'lower-case'
        bean.URL = 'upper-case'
        bean.cCompiler = 'lower-case first char'
        bean.CCompiler = 'upper-case first char'
        bean.cppCompiler = 'cppCompiler'
        bean.CPPCompiler = 'CPPCompiler'
        bean.a = 'some a'
        bean.b = 'some b'

        then:
        // Exercise getters
        bean.url == 'lower-case' && bean.getUrl() == bean.url
        bean.URL == 'upper-case' && bean.getURL() == bean.URL
        bean.cCompiler == 'lower-case first char' && bean.getcCompiler() == bean.cCompiler
        bean.CCompiler == 'upper-case first char' && bean.getCCompiler() == bean.CCompiler
        bean.cppCompiler == 'cppCompiler' && bean.getCppCompiler() == bean.cppCompiler
        bean.CPPCompiler == 'CPPCompiler' && bean.getCPPCompiler() == bean.CPPCompiler
        bean.a == 'some a' && bean.getA() == bean.a
        bean.b == 'some b' && bean.getb() == bean.b
    }

    static class DeviantBean {
        String gettingStarted() {
            'Getting started!'
        }
        boolean isidore() {
            true
        }
        void settings(String value) {}
    }

    def "deviant bean properties are not considered as such by Gradle"() {
        expect:
        !PropertyAccessorType.isGetterName('gettingStarted')
        !PropertyAccessorType.isGetterName('isidore')
        !PropertyAccessorType.isSetterName('settings')
    }

    def "deviant bean properties are considered as such by Groovy"() {
        when:
        def bean = new DeviantBean()
        bean.tings = 'Some settings'

        then:
        bean.tingStarted == 'Getting started!'
        bean.idore == true
    }

    static class StaticMethods {
        static int getStatic() {
            0
        }

        static boolean isStatic() {
            false
        }

        static void setStatic(int value) {}
    }

    def "static methods are not property accessors"() {
        expect:
        PropertyAccessorType.of(StaticMethods.getMethod("isStatic")) == null
        PropertyAccessorType.of(StaticMethods.getMethod("getStatic")) == null
        PropertyAccessorType.of(StaticMethods.getMethod("setStatic", int)) == null
    }
}
