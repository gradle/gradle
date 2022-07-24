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

package org.gradle.model

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.longlived.PersistentBuildProcessIntegrationTest
import org.gradle.model.internal.inspect.ModelRuleExtractor

@UnsupportedWithConfigurationCache(because = "software model")
class ModelRuleCachingIntegrationTest extends PersistentBuildProcessIntegrationTest {

    def setup() {
        buildFile << """
            def ruleCache = project.services.get($ModelRuleExtractor.name).cache
            def initialSize = ruleCache.size()
            gradle.buildFinished { println "### extracted new rules: \${ruleCache.size() > initialSize}" }
        """
    }

    boolean getNewRulesExtracted() {
        def match = output =~ /.*### extracted new rules: (true|false).*/
        match[0][1] == "true"
    }

    def "rules extracted from core plugins are reused across builds"() {
        given:
        buildFile << '''
            apply plugin: 'cpp'
        '''

        when:
        run()

        then:
        newRulesExtracted

        when:
        run()

        then:
        !newRulesExtracted
    }
}
