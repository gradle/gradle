/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.component

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.platform.base.ComponentSpecIdentifier
import org.gradle.platform.base.ModelInstantiationException
import spock.lang.Specification

class BaseComponentSpecTest extends Specification {
    def instantiator = new DirectInstantiator()
    def componentId = Mock(ComponentSpecIdentifier)
    FunctionalSourceSet functionalSourceSet;

    def setup() {
        functionalSourceSet = new DefaultFunctionalSourceSet("testFSS", new DirectInstantiator());
    }

    def "cannot instantiate directly"() {
        when:
        new BaseComponentSpec() {}

        then:
        def e = thrown ModelInstantiationException
        e.message == "Direct instantiation of a BaseComponentSpec is not permitted. Use a ComponentTypeBuilder instead."
    }

    def "cannot create instance of base class"() {
        when:
        BaseComponentSpec.create(BaseComponentSpec, componentId, functionalSourceSet, instantiator)

        then:
        def e = thrown ModelInstantiationException
        e.message == "Cannot create instance of abstract class BaseComponentSpec."
    }

    def "library has name, path and sensible display name"() {
        def component = BaseComponentSpec.create(MySampleComponent, componentId, functionalSourceSet, instantiator)

        when:
        _ * componentId.name >> "jvm-lib"
        _ * componentId.projectPath >> ":project-path"

        then:
        component.class == MySampleComponent
        component.name == "jvm-lib"
        component.projectPath == ":project-path"
        component.displayName == "MySampleComponent 'jvm-lib'"
    }

    def "create fails if subtype does not have a public no-args constructor"() {

        when:
        BaseComponentSpec.create(MyConstructedComponent, componentId, functionalSourceSet, instantiator)

        then:
        def e = thrown ModelInstantiationException
        e.message == "Could not create component of type MyConstructedComponent"
        e.cause instanceof IllegalArgumentException
        e.cause.message.startsWith "Could not find any public constructor for class"
    }

    def "contains sources of associated main sourceSet"() {
        when:
        def lss1 = languageSourceSet("lss1")
        functionalSourceSet.add(lss1)

        def component = BaseComponentSpec.create(MySampleComponent, componentId, functionalSourceSet, instantiator)

        and:
        def lss2 = languageSourceSet("lss2")
        functionalSourceSet.add(lss2)

        then:
        component.getSource() as List == [lss1, lss2]
    }

    def languageSourceSet(String name) {
        Stub(LanguageSourceSet) {
            getName() >> name
        }
    }

    static class MySampleComponent extends BaseComponentSpec {}
    static class MyConstructedComponent extends BaseComponentSpec {
        MyConstructedComponent(String arg) {}
    }
}
