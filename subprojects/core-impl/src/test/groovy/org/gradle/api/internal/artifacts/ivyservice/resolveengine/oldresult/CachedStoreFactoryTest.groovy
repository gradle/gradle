package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult

import org.gradle.api.artifacts.Configuration
import spock.lang.Specification

class CachedStoreFactoryTest extends Specification {

    def "stores results"() {
        def factory = new CachedStoreFactory(20)

        def conf1 = Mock(Configuration)
        def conf2 = Mock(Configuration)

        def results1 = Mock(TransientConfigurationResults)
        def results2 = Mock(TransientConfigurationResults)

        def store1 = factory.createCachedStore(conf1)
        def store1b = factory.createCachedStore(conf1)
        def store2 = factory.createCachedStore(conf2)

        when:
        store1.store(results1)
        store2.store(results2)

        then:
        store1.load() == results1
        store1b.load() == results1
        store2.load() == results2
    }
}
