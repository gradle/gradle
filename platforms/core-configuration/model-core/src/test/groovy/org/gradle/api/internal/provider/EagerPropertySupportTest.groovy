/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.api.internal.provider.EagerPropertySupport.setEager

class EagerPropertySupportTest extends Specification {
    @Shared
    def objects = TestUtil.objectFactory()

    def "sets a non-null value on #type"() {
        def property = create()
        property.convention(convention)

        when:
        setEager(property, value)

        then:
        property.getOrNull() == value

        where:
        type          | create                                      | value      | convention
        "Property"    | { objects.property(String) }                | "value"    | "convention"
        "ListProperty"| { objects.listProperty(String) }            | ["value"]  | ["convention"]
        "SetProperty" | { objects.setProperty(String) }             | ["value"] as Set | ["convention"] as Set
        "MapProperty" | { objects.mapProperty(String, String) }     | [k: "v"]   | [k: "convention"]
    }

    def "null on #type hides a convention set before and after, and unset() brings it back"() {
        def property = create()
        property.convention(convention)

        when:
        setEager(property, null)

        then:
        property.getOrNull() == null
        !property.present

        when:
        property.convention(laterConvention)

        then:
        property.getOrNull() == null

        when:
        property.unset()

        then:
        property.getOrNull() == laterConvention

        where:
        type           | create                                  | convention             | laterConvention
        "Property"     | { objects.property(String) }            | "convention"           | "later"
        "ListProperty" | { objects.listProperty(String) }        | ["convention"]         | ["later"]
        "SetProperty"  | { objects.setProperty(String) }         | ["convention"] as Set  | ["later"] as Set
        "MapProperty"  | { objects.mapProperty(String, String) } | [k: "convention"]      | [k: "later"]
    }

    def "null replaces an explicit value"() {
        def property = objects.property(String)
        property.set("value")

        when:
        setEager(property, null)

        then:
        property.getOrNull() == null
    }

    def "getEager returns null without sources, the single file collection, or the lazy collection"() {
        def files = objects.fileCollection()
        def single = objects.fileCollection().from("a")

        expect:
        EagerPropertySupport.getEager(files) == null

        when:
        files.setFrom(single)

        then:
        EagerPropertySupport.getEager(files).is(single)

        when:
        files.from("b")

        then:
        EagerPropertySupport.getEager(files).is(files)

        when:
        files.setFrom((Iterable) null)

        then:
        EagerPropertySupport.getEager(files) == null
    }

    def "getEager sees the sources of a convention"() {
        def files = objects.fileCollection()
        def single = objects.fileCollection().from("a")

        when:
        files.convention(single)

        then:
        EagerPropertySupport.getEager(files).is(single)
    }

    def "setting a property to its own view copies the view, other values stay live"() {
        def list = objects.listProperty(String)
        list.convention(["convention"])
        def map = objects.mapProperty(String, String)
        map.convention([k: "convention"])

        when:
        def listView = EagerPropertySupport.conventionKeepingView(list)
        listView.add("added")
        setEager(list, listView)
        def mapView = EagerPropertySupport.conventionKeepingView(map)
        mapView.put("added", "v")
        setEager(map, mapView)

        then:
        list.get() == ["convention", "added"]
        map.get() == [k: "convention", added: "v"]

        when:
        def alias = ["a"]
        setEager(list, alias)
        alias << "b"

        then:
        list.get() == ["a", "b"]
    }
}
