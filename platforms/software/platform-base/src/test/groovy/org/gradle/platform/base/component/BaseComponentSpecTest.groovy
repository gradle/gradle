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

import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.ModelInstantiationException
import org.gradle.platform.base.PlatformBaseSpecification
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier

class BaseComponentSpecTest extends PlatformBaseSpecification {
    def componentId = new DefaultComponentSpecIdentifier("p", "c")

    def "cannot instantiate directly"() {
        when:
        new BaseComponentSpec() {}

        then:
        def e = thrown ModelInstantiationException
        e.message == "Direct instantiation of a BaseComponentSpec is not permitted. Use a @ComponentType rule instead."
    }

    private <T extends ComponentSpec, I extends BaseComponentSpec> T create(Class<T> publicType, Class<I> implType) {
        return BaseComponentFixtures.create(publicType, implType, componentId)
    }

    def "library has name, path and sensible display name"() {
        when:
        def component = create(SampleComponent, MySampleComponent)

        then:
        component instanceof SampleComponent
        component.name == componentId.name
        component.projectPath == componentId.projectPath
        component.displayName == "SampleComponent '$componentId.name'"
        component.toString() == component.displayName
    }

    def "create fails if subtype does not have a public no-args constructor"() {

        when:
        create(ConstructedComponent, MyConstructedComponent)

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof ModelInstantiationException
        e.cause.message == "Could not create component of type ConstructedComponent"
        e.cause.cause instanceof IllegalArgumentException
        e.cause.cause.message.startsWith "Could not find any public constructor for class"
    }

    def "contains sources of associated main sourceSet"() {
        when:
        def component = create(SampleComponent, MySampleComponent)
        def lss1 = languageSourceSet("lss1")
        def lss2 = languageSourceSet("lss2")
        component.sources.put("lss1", lss1)
        component.sources.put("lss2", lss2)

        then:
        component.sources as List == [lss1, lss2]
    }

    def languageSourceSet(String name) {
        Stub(LanguageSourceSet) {
            getName() >> name
        }
    }

    interface SampleComponent extends ComponentSpec {}

    static class MySampleComponent extends BaseComponentSpec implements SampleComponent {}

    interface ConstructedComponent extends ComponentSpec {}

    static class MyConstructedComponent extends BaseComponentSpec implements ConstructedComponent {
        MyConstructedComponent(String arg) {}
    }
}
