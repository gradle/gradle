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

class ProviderIntegrationTest extends AbstractIntegrationSpec {

    public static final String DEFAULT_TEXT = 'default'
    public static final String CUSTOM_TEXT = 'custom'

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

    def "zip tracks task dependencies"() {
        buildFile << """
            tasks.register('myTask1', MyTask) {
                text.set('Hello')
            }
            tasks.register('myTask2', MyTask) {
                text.set('World')
            }

            tasks.register('combined', MyTask) {
                text.set(providers.zip(
                    tasks.named('myTask1').map { t -> t.text.get() },
                    tasks.named('myTask2').map { t -> t.text.get() }) { h, w ->
                    "\$h, \$w!"
                })
            }

            class MyTask extends DefaultTask {
                @Input
                final Property<String> text = project.objects.property(String).convention('$DEFAULT_TEXT')

                @TaskAction
                void printText() {
                    println text.get()
                }
            }
        """

        when:
        succeeds 'combined'

        then:
        executedAndNotSkipped(':myTask1', ':myTask2', ':combined')
        outputContains('Hello, World!')
    }

    def "can zip tasks directly"() {
        buildFile << """
            tasks.register('myTask1', MyTask) {
                text.set('Hello')
            }
            tasks.register('myTask2', MyTask) {
                text.set('World')
            }

            tasks.register('combined', MyTask) {
                text.set(providers.zip(
                    tasks.named('myTask1'),
                    tasks.named('myTask2')) { h, w ->
                    "\${h.text.get()}, \${w.text.get()}!"
                })
            }

            class MyTask extends DefaultTask {
                @Input
                final Property<String> text = project.objects.property(String).convention('$DEFAULT_TEXT')

                @TaskAction
                void printText() {
                    println text.get()
                }
            }
        """

        when:
        succeeds 'combined'

        then:
        executedAndNotSkipped(':myTask1', ':myTask2', ':combined')
        outputContains('Hello, World!')
    }

    def "can chain zipping by calling zip on provider directly"() {
        buildFile << """
            def t1 = tasks.register('myTask1', MyTask) {
                text.set('Black')
            }
            def t2 = tasks.register('myTask2', MyTask) {
                text.set('Lives')
            }

            def t3 = tasks.register('myTask3', MyTask) {
                text.set('Matter')
            }

            tasks.register('combined', MyTask) {
                text.set(
                    t1.zip(t2) { l, r -> "\${l.text.get()} \${r.text.get()}" }
                      .zip(t3) { l, r -> "\${l} \${r.text.get()}!" }
                )
            }

            class MyTask extends DefaultTask {
                @Input
                final Property<String> text = project.objects.property(String).convention('$DEFAULT_TEXT')

                @TaskAction
                void printText() {
                    println text.get()
                }
            }
        """

        when:
        succeeds 'combined'

        then:
        executedAndNotSkipped(':myTask1', ':myTask2', ':myTask3', ':combined')
        outputContains('Black Lives Matter!')
    }

    def "reasonable error message if one of zipped provider has no value"() {
        buildFile """
            tasks.register("run") {
                doLast {
                    def p1 = objects.property(String).convention("ok")
                    def p2 = objects.property(String)
                    def zipped = p1.zip(p2) { l, r -> l + r }
                    println zipped.get()
                }
            }
        """

        when:
        fails 'run'

        then:
        failure.assertHasErrorOutput("Provider has no value: property(java.lang.String, undefined)")
    }

    def "zipped provider is live"() {
        buildFile """
            tasks.register("run") {
                doLast {
                    def p1 = objects.property(String)
                    def p2 = objects.property(String)
                    def zipped = p1.zip(p2) { l, r -> l + " " + r }
                    p1.set("Beautiful")
                    p2.set("World")
                    println zipped.get()
                }
            }
        """

        when:
        succeeds 'run'

        then:
        outputContains("Beautiful World")
    }
}
