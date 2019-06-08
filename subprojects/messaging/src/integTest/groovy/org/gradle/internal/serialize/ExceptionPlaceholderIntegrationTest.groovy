/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.serialize

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ExceptionPlaceholderIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/1618")
    def "internal exception should not be thrown"() {
        given:
        buildFile << """
            apply plugin: 'java'

            ${jcenterRepository()}

            dependencies {
                testImplementation 'junit:junit:4.12'
                testImplementation 'org.mockito:mockito-core:2.3.7'
            }
        """

        file('src/test/java/example/Issue1618Test.java') << '''
            package example;
    
            import org.junit.Test;
    
            import static org.mockito.Mockito.doThrow;
            import static org.mockito.Mockito.mock;
    
            public class Issue1618Test {
    
                public static class Bugger {
                    public void run() {
                    }
                }
    
                @Test
                public void thisTestShouldBeMarkedAsFailed() {
                    RuntimeException mockedException = mock(RuntimeException.class);
                    Bugger bugger = mock(Bugger.class);
                    doThrow(mockedException).when(bugger).run();
                    bugger.run();
                }
            }
        '''

        expect:
        fails('test')
    }
}
