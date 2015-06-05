/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java.internal
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.LibraryComponentIdentifier
import org.gradle.internal.component.model.ComponentResolveMetaData
import org.gradle.language.base.internal.resolve.LanguageSourceSetLocalComponentFactory
import org.gradle.platform.base.DependencySpecContainer
import org.gradle.platform.base.internal.DefaultDependencySpec
import spock.lang.Specification
import spock.lang.Unroll

class DefaultJavaLocalComponentFactoryTest extends Specification {
    def "can convert java source set resolve context"() {
        given:
        def context = new DefaultJavaSourceSetResolveContext(':foo', Mock(DefaultJavaLanguageSourceSet))

        when:
        def factory = new LanguageSourceSetLocalComponentFactory()

        then:
        factory.canConvert(context)
    }

    def "can convert a simple java component"() {
        given: "a java sourceset that doesn't define any dependency"
        def sourceSet = Mock(DefaultJavaLanguageSourceSet)
        def dependencySpecs = Mock(DependencySpecContainer)
        dependencySpecs.dependencies >> { [] as Set }
        def project = ':myPath'

        dependencySpecs.iterator() >> { [].iterator() }
        sourceSet.parentName >> 'myLib'
        sourceSet.dependencies >> dependencySpecs

        def context = new DefaultJavaSourceSetResolveContext(project, sourceSet)

        when: "we create a local component factory"
        def factory = new LanguageSourceSetLocalComponentFactory()

        then: "the factory can convert the resolve context"
        factory.canConvert(context)

        when: "we convert the context to a local component"
        def component = factory.convert(context)

        then: "component metadata reflects the library configuration"
        component.id instanceof ModuleVersionIdentifier
        component.id.group == ':myPath'
        component.id.name == 'myLib'
        component.id.version == '<local component>'
        component.id.toString() == ':myPath:myLib:<local component>'

        when: "we create resolution metadata"
        def metadata = component.toResolveMetaData()

        then: "metadata reflects the appropriate library information"
        metadata instanceof ComponentResolveMetaData
        metadata.componentId instanceof LibraryComponentIdentifier
        metadata.componentId.displayName == /project ':myPath' library 'myLib'/
        metadata.dependencies.empty
        !metadata.changing
        metadata.configurationNames == [LibraryComponentIdentifier.API_CONFIGURATION_NAME] as Set
        metadata.source == null
    }

    @Unroll
    def "can convert a java component with #dependenciesDescriptor"() {
        given: "a java sourceset that defines dependencies"
        def sourceSet = Mock(DefaultJavaLanguageSourceSet)
        def dependencySpecs = Mock(DependencySpecContainer)
        def project = ':myPath'

        sourceSet.parentName >> 'myLib'
        sourceSet.dependencies >> dependencySpecs

        def context = new DefaultJavaSourceSetResolveContext(project, sourceSet)

        when: "we create a local component factory"
        def factory = new LanguageSourceSetLocalComponentFactory()

        then: "the factory can convert the resolve context"
        factory.canConvert(context)

        when: "we convert the context to a local component"
        dependencySpecs.dependencies >> dependencies
        def component = factory.convert(context)

        then: "component metadata reflects the library configuration"
        component.id instanceof ModuleVersionIdentifier
        component.id.group == ':myPath'
        component.id.name == 'myLib'
        component.id.version == '<local component>'
        component.id.toString() == ':myPath:myLib:<local component>'

        when: "we create resolution metadata"
        def metadata = component.toResolveMetaData()

        then: "metadata reflects the appropriate library information"
        metadata instanceof ComponentResolveMetaData
        metadata.componentId instanceof LibraryComponentIdentifier
        metadata.componentId.displayName == /project ':myPath' library 'myLib'/
        !metadata.changing
        metadata.configurationNames == [LibraryComponentIdentifier.API_CONFIGURATION_NAME] as Set
        metadata.source == null

        and: "component metadata dependencies correspond to the defined dependencies"
        metadata.dependencies.size() == dependencies.size()
        dependencies.eachWithIndex { spec, i ->
            def componentDep = metadata.dependencies[i]
            assert componentDep.requested.group == spec.projectPath?:project
            assert componentDep.requested.name == spec.libraryName
            assert componentDep.requested.version == '<local component>'
        }

        where:
        dependencies                                                                                        | dependenciesDescriptor
        [new DefaultDependencySpec('someLib', ':myPath')]                                                   | 'single dependency with explicit project'
        [new DefaultDependencySpec('someLib', ':myPath'), new DefaultDependencySpec('someLib2', ':myPath')] | '2 deps on the same project'
        [new DefaultDependencySpec('someLib', ':myPath'), new DefaultDependencySpec('someLib', ':myPath2')] | '2 deps on 2 different projects'
        [new DefaultDependencySpec('someLib', null)]                                                        | 'a single dependency on the current project'

    }
}
