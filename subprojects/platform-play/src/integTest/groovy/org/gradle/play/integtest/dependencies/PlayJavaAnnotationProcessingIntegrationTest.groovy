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

package org.gradle.play.integtest.dependencies

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

import static org.gradle.play.integtest.fixtures.Repositories.PLAY_REPOSITORIES

@Issue("https://github.com/gradle/gradle/issues/2337")
@Requires(TestPrecondition.JDK8_OR_LATER)
class PlayJavaAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    def "can compile Java class incorporating annotation processing"() {
        given:
        buildFile << """
            plugins {
                id 'play'
            }
            
            $PLAY_REPOSITORIES

            dependencies {
                play 'org.projectlombok:lombok:1.16.22'
            }
        """

        file("app/controller/GetterSetterExample.java") << """
            package controller;

            import lombok.AccessLevel;
            import lombok.Getter;
            import lombok.Setter;
            
            public class GetterSetterExample {
                @Getter
                @Setter
                private int age = 10;
                
                @Setter(AccessLevel.PROTECTED)
                private String name;
            
                @Override 
                public String toString() {
                    return String.format("%s (age: %d)", name, getAge());
                }
            }
        """

        when:
        succeeds('compilePlayBinaryScala')

        then:
        executedAndNotSkipped(':compilePlayBinaryScala')
    }
}
