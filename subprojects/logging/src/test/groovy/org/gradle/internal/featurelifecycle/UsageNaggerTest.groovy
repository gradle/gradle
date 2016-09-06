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

package org.gradle.internal.featurelifecycle

import org.gradle.internal.Factory
import org.gradle.internal.notfeaturelifecycle.GroovyNaggingSource
import org.gradle.internal.notfeaturelifecycle.JavaNaggingSource
import org.gradle.internal.notfeaturelifecycle.NaggingSource
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.GradleVersion
import spock.lang.Subject
import spock.lang.Unroll

@Subject(UsageNagger)
class UsageNaggerTest extends ConcurrentSpec {

    private static final String NEXT_GRADLE_VERSION = GradleVersion.current().getNextMajor().getVersion()


    def reporter = new CollectingLocationReporter()
    def UsageNagger usageNagger = new UsageNagger(UsageNagger)

    def setup() {
        usageNagger.useLocationReporter(reporter)
    }

    def cleanup() {
        usageNagger.reset()
    }

    def 'nagUserWith nags more than once'() {
        when:
        usageNagger.nagUserWith('feature1')
        usageNagger.nagUserWith('feature2')
        usageNagger.nagUserWith('feature2')

        then:
        def usages = reporter.usages
        usages.size() == 3

        and:
        usages[0].message == 'feature1'
        usages[1].message == 'feature2'
        usages[2].message == 'feature2'
    }

    def 'nagUserOnceWith nags only once'() {
        when:
        usageNagger.nagUserOnceWith('feature1')
        usageNagger.nagUserOnceWith('feature2')
        usageNagger.nagUserOnceWith('feature2')

        then:
        def usages = reporter.usages
        usages.size() == 2

        and:
        usages[0].message == 'feature1'
        usages[1].message == 'feature2'
    }

    def 'nagUserOnceWith nags only once, unless reset is called.'() {
        when:
        usageNagger.nagUserOnceWith('feature1')
        usageNagger.nagUserOnceWith('feature2')
        usageNagger.nagUserOnceWith('feature2')
        usageNagger.reset()
        usageNagger.nagUserOnceWith('feature1')
        usageNagger.nagUserOnceWith('feature2')
        usageNagger.nagUserOnceWith('feature2')
        usageNagger.reset()
        usageNagger.nagUserOnceWith('feature1')
        usageNagger.nagUserOnceWith('feature2')
        usageNagger.nagUserOnceWith('feature2')

        then:
        def usages = reporter.usages
        usages.size() == 6

        and:
        usages[0].message == 'feature1'
        usages[1].message == 'feature2'
        usages[2].message == 'feature1'
        usages[3].message == 'feature2'
        usages[4].message == 'feature1'
        usages[5].message == 'feature2'
    }

    def 'does not nag from factory while disabled'() {
        given:
        Factory<String> factory = Mock(Factory)

        when:
        def result = usageNagger.whileDisabled(factory)

        then:
        result == 'result'

        and:
        1 * factory.create() >> {
            usageNagger.nagUserWith('nag')
            return 'result'
        }
        0 * _

        then:
        reporter.usages.isEmpty()
    }

    def 'does not nag from action while disabled'() {
        given:
        def action = Mock(Runnable)

        when:
        usageNagger.whileDisabled(action)

        then:
        _ * action.run() >> {
            usageNagger.nagUserWith('nag')
        }
        0 * _

        then:
        reporter.usages.isEmpty()
    }

    def 'warnings are disabled for the current thread only'() {
        when:
        async {
            start {
                thread.blockUntil.disabled
                usageNagger.nagUserWith('nag')
                instant.logged
            }
            start {
                usageNagger.whileDisabled {
                    instant.disabled
                    usageNagger.nagUserWith('ignored')
                    thread.blockUntil.logged
                }
            }
        }

        then:
        def usages = reporter.usages

        usages.size() == 1
        usages[0].message == 'nag'
    }

    def 'nested whileDisabled works with Runnables'() {
        when:
        usageNagger.nagUserWith('nag1')
        usageNagger.whileDisabled {
            usageNagger.nagUserWith('ignored1')
            usageNagger.whileDisabled {
                usageNagger.nagUserWith('nested-ignored')
            }
            usageNagger.nagUserWith('ignored2')
        }
        usageNagger.nagUserWith('nag2')

        then:
        def usages = reporter.usages

        usages.size() == 2
        usages[0].message == 'nag1'
        usages[1].message == 'nag2'
    }

    def 'nested whileDisabled works with Factories'() {
        when:
        usageNagger.nagUserWith('nag1')
        usageNagger.whileDisabled new Factory<String>() {
            @Override
            String create() {
                usageNagger.nagUserWith('ignored1')
                usageNagger.whileDisabled new Factory<String>() {
                    @Override
                    String create() {
                        usageNagger.nagUserWith('nested-ignored')
                        return 'bar'
                    }
                }
                usageNagger.nagUserWith('ignored2')
                return 'foo'
            }
        }
        usageNagger.nagUserWith('nag2')

        then:
        def usages = reporter.usages

        usages.size() == 2
        usages[0].message == 'nag1'
        usages[1].message == 'nag2'
    }

    def 'nested whileDisabled works with Runnable and Factory'() {
        when:
        usageNagger.nagUserWith('nag1')
        usageNagger.whileDisabled {
            usageNagger.nagUserWith('ignored1')
            usageNagger.whileDisabled new Factory<String>() {
                @Override
                String create() {
                    usageNagger.nagUserWith('nested-ignored')
                    return 'bar'
                }
            }
            usageNagger.nagUserWith('ignored2')
        }
        usageNagger.nagUserWith('nag2')

        then:
        def usages = reporter.usages

        usages.size() == 2
        usages[0].message == 'nag1'
        usages[1].message == 'nag2'
    }

    @Unroll
    def 'Example #simpleSourceClassName results in expected call stack.'() {
        when:
        source.execute()

        then:
        def usages = reporter.usages

        usages.size() == 3
        usages[0].message == NaggingSource.DIRECT_CALL
        usages[1].message == NaggingSource.INDIRECT_CALL
        usages[2].message == NaggingSource.INDIRECT_CALL_2

        and:
        usages[0].stack[0].className == sourceClassName
        usages[1].stack[0].className == sourceClassName
        usages[2].stack[0].className == sourceClassName

        and:
        usages[0].stack[0].methodName == 'create'

        usages[1].stack[0].methodName == 'create'
        usages[1].stack[1].methodName == 'indirectly'

        usages[2].stack[0].methodName == 'create'
        usages[2].stack[1].methodName == 'indirectly'
        usages[2].stack[2].methodName == 'indirectly2'

        where:
        source << [new JavaNaggingSource(), new GroovyNaggingSource()]
        sourceClassName = source.class.name
        simpleSourceClassName = source.class.simpleName
    }

    def 'DeprecationNagger methods create expected messages.'() {
        setup:
        DeprecationNagger deprecationNagger = usageNagger

        when:
        deprecationNagger.nagUserOfDeprecated('thing')
        deprecationNagger.nagUserOfDeprecated('thing', 'explanation')
        deprecationNagger.nagUserOfDeprecatedBehaviour('behaviour')
        deprecationNagger.nagUserOfDiscontinuedApi('api', 'advice')
        deprecationNagger.nagUserOfDiscontinuedMethod('methodName')
        deprecationNagger.nagUserOfDiscontinuedMethod('methodName', 'advice')
        deprecationNagger.nagUserOfDiscontinuedProperty('propertyName', 'advice')
        deprecationNagger.nagUserOfPluginReplacedWithExternalOne('pluginName', 'replacement')
        deprecationNagger.nagUserOfReplacedMethod('methodName', 'replacement')
        deprecationNagger.nagUserOfReplacedNamedParameter('parameterName', 'replacement')
        deprecationNagger.nagUserOfReplacedPlugin('pluginName', 'replacement')
        deprecationNagger.nagUserOfReplacedProperty('propertyName', 'replacement')
        deprecationNagger.nagUserOfReplacedTask('taskName', 'replacement')
        deprecationNagger.nagUserOfReplacedTaskType('taskName', 'replacement')

        and:
        def messages = reporter.usages.collect {
            it.message
        }

        then:

        messages.size() == 14

        // produces better assertion failure messages than
        // messages == expectedMessages
        (0..13).each {
            assert messages[it] == expectedMessages[it]
        }

        where:
        expectedMessages = [
            'thing has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '.',
            'thing has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. explanation',
            'behaviour. This behaviour has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '.',
            'The api has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. advice',
            'The methodName method has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '.',
            'The methodName method has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. advice',
            'The propertyName property has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. advice',
            'The pluginName plugin has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. Consider using the replacement plugin instead.',
            'The methodName method has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. Please use the replacement method instead.',
            'The parameterName named parameter has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. Please use the replacement named parameter instead.',
            'The pluginName plugin has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. Please use the replacement plugin instead.',
            'The propertyName property has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. Please use the replacement property instead.',
            'The taskName task has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. Please use the replacement task instead.',
            'The taskName task type has been deprecated and is scheduled to be removed in Gradle ' + NEXT_GRADLE_VERSION + '. Please use the replacement instead.',
        ]
    }

    def 'IncubationNagger methods create expected messages.'() {
        setup:
        IncubationNagger incubationNagger = usageNagger

        when:
        incubationNagger.incubatingFeatureUsed('incubatingFeature')
        incubationNagger.incubatingFeatureUsed('incubatingFeature', 'additionalWarning')

        and:
        def messages = reporter.usages.collect {
            it.message
        }

        then:

        messages.size() == 2

        // produces better assertion failure messages than
        // messages == expectedMessages
        (0..1).each {
            assert messages[it] == expectedMessages[it]
        }

        where:
        expectedMessages = [
            'incubatingFeature is an incubating feature.',
            'incubatingFeature is an incubating feature.\nadditionalWarning',
        ]
    }

    def 'BasicNagger methods create expected messages.'() {
        setup:
        BasicNagger basicNagger = usageNagger

        when:
        basicNagger.nagUserWith('message')
        basicNagger.nagUserOnceWith('message')

        and:
        def messages = reporter.usages.collect {
            it.message
        }

        then:

        messages.size() == 2

        // produces better assertion failure messages than
        // messages == expectedMessages
        (0..1).each {
            assert messages[it] == expectedMessages[it]
        }

        where:
        expectedMessages = [
            'message',
            'message',
        ]
    }
}
