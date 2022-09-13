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

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.util.internal.VersionNumber
import spock.lang.Specification

import java.beans.Introspector

import static org.junit.Assume.assumeTrue

class PropertyAccessorTypeTest extends Specification {
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
        String getccCompiler() { "CC" }

        String isNotString() { return "string" }
        Boolean isNotBoolean() { return true }

        DeviantBean setWriteOnly(String s) {
            return this
        }
    }

    def "deviant bean properties are considered as such by Gradle"() {
        expect:
        PropertyAccessorType.fromName('gettingStarted') == PropertyAccessorType.GET_GETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("gettingStarted")) == PropertyAccessorType.GET_GETTER
        PropertyAccessorType.fromName('getccCompiler') == PropertyAccessorType.GET_GETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("getccCompiler")) == PropertyAccessorType.GET_GETTER
        PropertyAccessorType.fromName('isidore') == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("isidore")) == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.fromName('settings') == PropertyAccessorType.SETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("settings", String)) == PropertyAccessorType.SETTER
    }

    def "deviant bean properties are considered as such by Java"() {
        expect:
        def propertyNames = Introspector.getBeanInfo(DeviantBean).propertyDescriptors.collect { it.name }
        propertyNames.contains("tingStarted")
        propertyNames.contains("ccCompiler")
        propertyNames.contains("idore")
        propertyNames.contains("tings")
    }

    def "deviant bean properties are considered as such by Groovy"() {
        when:
        def bean = new DeviantBean()
        bean.tings = 'Some settings'

        then:
        bean.tingStarted == 'Getting started!'
        bean.idore == true
    }

    def "is methods with Boolean return type are considered as such by Gradle and Groovy but not Java"() {
        assumeTrue('This test requires bundled Groovy 3', VersionNumber.parse(GroovySystem.version).major == 3)
        def bean = new DeviantBean()
        def propertyNames = Introspector.getBeanInfo(DeviantBean).propertyDescriptors.collect { it.name }

        expect:
        bean.notBoolean == true
        try {
            bean.notString
            assert false
        } catch (MissingPropertyException e) {
            assert e.property == "notString"
        }

        PropertyAccessorType.fromName('isNotBoolean') == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("isNotBoolean")) == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.fromName('isNotString') == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("isNotString")) == null

        !propertyNames.contains("notBoolean")
        !propertyNames.contains("notString")
    }

    /**
     * See <a href="https://issues.apache.org/jira/browse/GROOVY-10708">GROOVY-10708</a>
     */
    def "is methods with non-primitive boolean return type are not considered properties by Gradle, Groovy nor Java"() {
        assumeTrue('This test requires bundled Groovy 4 or later', VersionNumber.parse(GroovySystem.version).major >= 4)

        given:
        def bean = new DeviantBean()

        when:
        bean.notBoolean
        then:
        thrown MissingPropertyException

        when:
        bean.notString
        then:
        thrown MissingPropertyException

        expect:
        PropertyAccessorType.fromName('isNotBoolean') == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("isNotBoolean")) == null
        PropertyAccessorType.fromName('isNotString') == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("isNotString")) == null

        when:
        def propertyNames = Introspector.getBeanInfo(DeviantBean).propertyDescriptors.collect { it.name }
        then:
        !propertyNames.contains("notBoolean")
        !propertyNames.contains("notString")
    }

    def "setter methods with non-void return type are considered as such by Gradle and Groovy but not Java"() {
        def bean = new DeviantBean()
        def propertyNames = Introspector.getBeanInfo(DeviantBean).propertyDescriptors.collect { it.name }

        when:
        bean.writeOnly = "ok"

        then:
        PropertyAccessorType.fromName('setWriteOnly') == PropertyAccessorType.SETTER
        PropertyAccessorType.of(DeviantBean.class.getMethod("setWriteOnly", String)) == PropertyAccessorType.SETTER

        !propertyNames.contains("writeOnly")
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

    static class DeviantProviderMethods {
        Provider<Boolean> isNotBoolean() {
            null
        }

        Property<Boolean> isStillNotBoolean() {
            null
        }
    }

    def "is methods with Provider/Property of Boolean return type are not considered as such by Gradle, Groovy and Java"() {
        def bean = new DeviantProviderMethods()
        def propertyNames = Introspector.getBeanInfo(DeviantProviderMethods).propertyDescriptors.collect { it.name }

        expect:
        try {
            bean.notBoolean
            assert false
        } catch (MissingPropertyException e) {
            assert e.property == "notBoolean"
        }

        try {
            bean.stillNotBoolean
            assert false
        } catch (MissingPropertyException e) {
            assert e.property == "stillNotBoolean"
        }

        PropertyAccessorType.fromName('isNotBoolean') == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.of(DeviantProviderMethods.class.getMethod("isNotBoolean")) == null
        PropertyAccessorType.fromName('isStillNotBoolean') == PropertyAccessorType.IS_GETTER
        PropertyAccessorType.of(DeviantProviderMethods.class.getMethod("isStillNotBoolean")) == null

        !propertyNames.contains("notBoolean")
        !propertyNames.contains("stillNotBoolean")
    }
}
