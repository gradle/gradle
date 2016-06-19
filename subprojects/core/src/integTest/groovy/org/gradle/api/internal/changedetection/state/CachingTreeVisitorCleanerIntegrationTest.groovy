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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CachingTreeVisitorCleanerIntegrationTest extends AbstractIntegrationSpec {

    def "cache should get flushed by CachingTreeVisitorCleaner before other build listeners get called"() {
        when:
        buildFile << '''
            import org.gradle.api.internal.changedetection.state.CachingTreeVisitor

            apply plugin:'java'

            dependencies {
                testCompile 'junit:junit:4.12'
            }

            repositories {
                jcenter()
            }

            gradle.buildFinished {
                def cachingTreeVisitor = gradle.getServices().get(CachingTreeVisitor)
                // inspect internal/private state of CachingTreeVisitor
                assert cachingTreeVisitor.cachedTrees.size() == 0 : "Cache wasn't flushed by CachingTreeVisitorCleaner"
            }
        '''
        file('src/main/java/Hello.java') << 'public class Hello {}'
        file('src/test/java/HelloTest.java') << 'public class HelloTest {}'

        then:
        succeeds("build")
    }

}
