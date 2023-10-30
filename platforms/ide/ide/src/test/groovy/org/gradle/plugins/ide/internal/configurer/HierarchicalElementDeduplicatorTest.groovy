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
package org.gradle.plugins.ide.internal.configurer

import com.google.common.collect.Lists
import spock.lang.Specification

class HierarchicalElementDeduplicatorTest extends Specification {

    def "unique element names are not deduplicated"() {
        given:
        element("root") {
            element("foo") {
                element("bar") {}
            }
            element("foobar") {
                element("app") {}
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:foo") == "foo"
        elementName("root:foo:bar") == "bar"
        elementName("root:foobar") == "foobar"
        elementName("root:foobar:app") == "app"
    }

    def "deduplicates elements with the same name"() {
        given:
        element("root") {
            element("foo") {
                element("app") {}
            }
            element("bar") {
                element("app") {}
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:foo") == "foo"
        elementName("root:foo:app") == "foo-app"
        elementName("root:bar") == "bar"
        elementName("root:bar:app") == "bar-app"
    }

    def "dedups child element with same name as parent element"() {
        given:
        element("root") {
            element("app") {
                element("app") {}
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:app") == "root-app"
        elementName("root:app:app") == "app-app"

    }

    def "handles calculated name matches existing element name"() {
        given:
        element("root") {
            element("root-foo-bar") {}
            element("foo-bar") {}
            element("foo") {
                element("bar") {}
            }
            element("baz") {
                element("bar") {}
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:root-foo-bar") == "root-root-foo-bar"
        elementName("root:foo-bar") == "foo-bar"
        elementName("root:foo") == "foo"
        elementName("root:foo:bar") == "root-foo-bar"
        elementName("root:baz:bar") == "baz-bar"
    }

    def "dedups elements with different nested level"() {
        given:
        element("root") {
            element("app") {}
            element("services") {
                element("bar") {
                    element("app") {}
                }
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:app") == "root-app"
        elementName("root:services") == "services"
        elementName("root:services:bar") == "bar"
        elementName("root:services:bar:app") == "bar-app"
    }

    def "dedups root element name"() {
        given:
        element("app") {
            element("app") {}
        }

        when:
        deduplicateNames()

        then:
        elementName("app") == "app"
        elementName("app:app") == "app-app"
    }


    def "deduplication works on deduplicated parent module name"() {
        given:
        element("root") {
            element("bar") {
                element("services") {
                    element("rest") {}
                }
            }
            element("foo") {
                element("services") {
                    element("rest") {}
                }
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:bar:services") == "bar-services"
        elementName("root:bar:services:rest") == "bar-services-rest"
        elementName("root:foo:services") == "foo-services"
        elementName("root:foo:services:rest") == "foo-services-rest"
    }

    def "allows deduplication with parent not part of the target list"() {
        given:
        element("root") {
            element("bar") {
                element("services") {
                    element("rest") {}
                }
            }
            element("foo") {
                element("services") {
                    element("rest") {}
                }
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:bar:services") == "bar-services"
        elementName("root:bar:services:rest") == "bar-services-rest"
        elementName("root:foo:services") == "foo-services"
        elementName("root:foo:services:rest") == "foo-services-rest"
    }

    def "removes duplicate words from element dedup prefix"() {
        given:
        element("root"){
            element("api"){
                element("myelement") {
                    element("myelement-foo") {
                        element("app") {}
                    }
                }

            }
            element("impl"){
                element("myelement") {
                    element("myelement-foo") {
                        element("app") {}
                    }
                }
            }
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:api") == "api"
        elementName("root:api:myelement") == "api-myelement"
        elementName("root:api:myelement:myelement-foo") == "api-myelement-foo"
        elementName("root:api:myelement:myelement-foo:app") == "api-myelement-foo-app"

        elementName("root:impl") == "impl"
        elementName("root:impl:myelement") == "impl-myelement"
        elementName("root:impl:myelement:myelement-foo") == "impl-myelement-foo"
        elementName("root:impl:myelement:myelement-foo:app") == "impl-myelement-foo-app"
    }

    def "Names are not simplified if that would create a name clash"() {
        given:
        element("root"){
            element("root-myelement") {
                element("myelement-foo") {
                }
            }
            element("myelement-foo")
        }

        when:
        deduplicateNames()

        then:
        elementName("root:myelement-foo") == "root-myelement-foo"
        elementName("root:root-myelement:myelement-foo") == "root-myelement-myelement-foo"
    }

    def "dedups elements using identity names if available"() {
        given:
        element("root") {
            idNameElement("app") {}
            element("app") {}
            idNameElement("services") {
                idNameElement("bar") {
                    element("app") {}
                    idNameElement("app") {}
                }
            }
            element("services")
        }

        when:
        deduplicateNames()

        then:
        elementName("root") == "root"
        elementName("root:app") == "root-app"
        elementName("root:appId") == "root-appId"
        elementName("root:servicesId") == "root-servicesId"
        elementName("root:services") == "root-services"
        elementName("root:servicesId:barId") == "bar"
        elementName("root:servicesId:barId:app") == "root-services-bar-app"
        elementName("root:servicesId:barId:appId") == "root-services-bar-appId"
    }

    List<DummyElement> elements = Lists.newArrayList()

    private element(String name, Closure config = {}) {
        def root = new DummyElement(name)
        elements += root
        configure(root, config)
    }

    private idNameElement(String name, Closure config = {}) {
        def root = new DummyElement(name, name + "Id")
        elements += root
        configure(root, config)
    }

    private configure(Object target, Closure config) {
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.delegate = target
        config.call()
    }

    private deduplicateNames() {
        new HierarchicalElementDeduplicator<DummyElement>(new DummyAdapter()).deduplicate(elements).each { element, name ->
            element.newName = name
        }
    }

    private elementName(String path) {
        elements.find { it.path == path }.newName
    }

    private class DummyElement {
        String name
        String identityName
        String newName
        DummyElement parent

        DummyElement(String name, String identityName = name) {
            this.name = name
            this.identityName = identityName
            this.newName = name
        }

        def element(String name, Closure config = {}) {
            def child = new DummyElement(name)
            child.parent = this
            configure(child, config)
            elements += child
        }

        def idNameElement(String name, Closure config = {}) {
            def child = new DummyElement(name, name + "Id")
            child.parent = this
            configure(child, config)
            elements += child
        }

        String getPath() {
            if (parent == null) {
                return identityName
            } else {
                return parent.path + ':' + identityName
            }
        }
    }

    private class DummyAdapter implements HierarchicalElementAdapter<DummyElement> {
        String getName(DummyElement element) {
            return element.name
        }

        String getIdentityName(DummyElement element) {
            return element.identityName
        }

        DummyElement getParent(DummyElement element) {
            return element.parent
        }

    }

}
