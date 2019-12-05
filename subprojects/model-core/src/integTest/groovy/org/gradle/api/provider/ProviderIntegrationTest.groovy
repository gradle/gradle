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

package org.gradle.api.provider

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

class ProviderIntegrationTest extends AbstractIntegrationSpec {

    public static final String DEFAULT_TEXT = 'default'
    public static final String CUSTOM_TEXT = 'custom'

    @ToBeFixedForInstantExecution
    def "can create provider and retrieve immutable value"() {
        given:
        buildFile << """
            task myTask(type: MyTask)

            class MyTask extends DefaultTask {
                Provider<String> text = project.provider { '$DEFAULT_TEXT' }

                @Internal
                String getText() {
                    text.get()
                }
                
                void setText(Provider<String> text) {
                    this.text = text
                }

                @TaskAction
                void printText() {
                    println getText()
                }
            }
        """

        when:
        succeeds('myTask')

        then:
        outputContains(DEFAULT_TEXT)

        when:
        buildFile << """
            myTask.text = project.provider { '$CUSTOM_TEXT' }
        """
        succeeds('myTask')

        then:
        outputContains(CUSTOM_TEXT)
    }

    def "can inject and use provider factory via annotation"() {
        file("buildSrc/src/main/java/MyTask.java") << """
            import ${DefaultTask.name};
            import ${Internal.name};
            import ${Provider.name};
            import ${ProviderFactory.name};
            import ${TaskAction.name};

            import javax.inject.Inject;
            import java.util.concurrent.Callable;

            public class MyTask extends DefaultTask {
                private final Provider<String> text;
 
                @Inject
                public MyTask(ProviderFactory providerFactory) {
                    text = providerFactory.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "$DEFAULT_TEXT";
                        }
                    });
                }
                
                @Internal
                public String getText() {
                    return text.get();
                }
                
                @Internal
                public Boolean getRenderText() {
                    return getProviderFactory().provider(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return $renderText;
                        }
                    }).get();
                }
                
                @Inject
                public ProviderFactory getProviderFactory() {
                    throw new UnsupportedOperationException();
                }

                @TaskAction
                public void doSomething() {
                    if (getRenderText()) {
                        System.out.println(getText());
                    }
                }
            }
        """
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        result.normalizedOutput.contains(DEFAULT_TEXT) == renderText

        where:
        renderText << [false, true]
    }
}
