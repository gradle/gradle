/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;


import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver
import org.gradle.internal.Factory
import org.gradle.internal.reflect.Instantiator
import org.gradle.listener.ListenerManager
import org.gradle.util.HelperUtil
import spock.lang.Specification

/**
 * @author Hans Dockter, Szczepan
 */
public class DefaultConfigurationContainerSpec extends Specification {
    private static final String TEST_DESCRIPTION = "testDescription";
    private static final Closure TEST_CLOSURE = HelperUtil.createSetterClosure("Description", TEST_DESCRIPTION);
    private static final String TEST_NAME = "testName";

    private ArtifactDependencyResolver dependencyResolver = Mock()
    private Instantiator instantiator = Mock()
    private DomainObjectContext domainObjectContext = Mock()
    private ListenerManager listenerManager = Mock()
    private DependencyMetaDataProvider metaDataProvider = Mock()
    private Factory<ResolutionStrategyInternal> resolutionStrategyFactory = Mock()

    def ConfigurationInternal conf = Mock()

    private DefaultConfigurationContainer configurationContainer = new DefaultConfigurationContainer(
            dependencyResolver, instantiator, domainObjectContext,
            listenerManager, metaDataProvider, resolutionStrategyFactory);

    def "adds and gets"() {
        _ * conf.getName() >> "compile"
        1 * domainObjectContext.absoluteProjectPath("compile") >> ":compile"
        1 * instantiator.newInstance(DefaultConfiguration.class, ":compile", "compile", configurationContainer,
                dependencyResolver, listenerManager, metaDataProvider, resolutionStrategyFactory) >> conf

        when:
        def compile = configurationContainer.add("compile")

        then:
        configurationContainer.getByName("compile") == compile

        when:
        configurationContainer.getByName("fooo")

        then:
        thrown(UnknownConfigurationException)
    }

    def "configures and finds"() {
        _ * conf.getName() >> "compile"
        1 * domainObjectContext.absoluteProjectPath("compile") >> ":compile"
        1 * instantiator.newInstance(DefaultConfiguration.class, ":compile", "compile", configurationContainer,
                dependencyResolver, listenerManager, metaDataProvider, resolutionStrategyFactory) >> conf

        when:
        def compile = configurationContainer.add("compile") {
            description = "I compile!"
        }

        then:
        configurationContainer.getByName("compile") == compile
        1 * conf.setDescription("I compile!")

        //finds configurations
        configurationContainer.findByName("compile") == compile
        configurationContainer.findByName("fooo") == null
        configurationContainer.findAll { it.name == "compile" } as Set == [compile] as Set
        configurationContainer.findAll { it.name == "fooo" } as Set == [] as Set

        configurationContainer as List == [compile] as List
    }

    def "creates detached"() {
        given:
        def dependency1 = HelperUtil.createDependency("group1", "name1", "version1");
        def dependency2 = HelperUtil.createDependency("group2", "name2", "version2");

        when:
        def detached = configurationContainer.detachedConfiguration(dependency1, dependency2);

        then:
        detached.getAll() == [detached] as Set
        detached.getHierarchy() == [detached] as Set
        [dependency1, dependency2].each { detached.getDependencies().contains(it) }
        detached.getDependencies().size() == 2
    }
}
