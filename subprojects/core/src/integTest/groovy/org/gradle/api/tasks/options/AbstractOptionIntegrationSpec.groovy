/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks.options

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractOptionIntegrationSpec extends AbstractIntegrationSpec {
    String taskWithSingleOption(String optionType) {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;

            import java.util.List;
            
            public class SampleTask extends DefaultTask {
                private $optionType myProp;
                
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public void setMyProp($optionType myProp) {
                    this.myProp = myProp;
                }
                
                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp);
                }
                
                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String taskWithFlagMethod() {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;

            import java.util.List;
            
            public class SampleTask extends DefaultTask {
                private boolean myProp;
                
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public void active() {
                    this.myProp = true;
                }
                
                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp);
                }
            }
        """
    }

    String groovyTaskWithSingleOption(String optionType) {
        """
            public class SampleTask extends DefaultTask {
                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                $optionType myProp

                @TaskAction
                public void renderOptionValue() {
                    println("Value of myProp: " + myProp)
                }
                
                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String taskWithSinglePropertyOption(String optionType) {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.Internal;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.provider.Property;

            import java.util.List;

            public class SampleTask extends DefaultTask {
                private final Property<$optionType> myProp = getProject().getObjects().property(${optionType}.class);

                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public Property<$optionType> getMyProp() {
                    return myProp;
                }

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp.getOrNull());
                }

                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String groovyTaskWithSinglePropertyOption(String optionType) {
        """
            public class SampleTask extends DefaultTask {
                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                final Property<$optionType> myProp = project.objects.property($optionType)

                @TaskAction
                public void renderOptionValue() {
                    println("Value of myProp: " + myProp.getOrNull())
                }

                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String taskWithListPropertyOption(String optionType) {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.Internal;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.provider.ListProperty;

            public class SampleTask extends DefaultTask {
                private final ListProperty<$optionType> myProp = getProject().getObjects().listProperty(${optionType}.class);

                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public ListProperty<$optionType> getMyProp() {
                    return myProp;
                }

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp.getOrNull());
                }

                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String groovyTaskWithListPropertyOption(String optionType) {
        """
            public class SampleTask extends DefaultTask {
                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                final ListProperty<$optionType> myProp = project.objects.listProperty($optionType)

                @TaskAction
                public void renderOptionValue() {
                    println("Value of myProp: " + myProp.getOrNull())
                }

                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String taskWithSetPropertyOption(String optionType) {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.Internal;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.provider.SetProperty;

            public class SampleTask extends DefaultTask {
                private final SetProperty<$optionType> myProp = getProject().getObjects().setProperty(${optionType}.class);

                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public SetProperty<$optionType> getMyProp() {
                    return myProp;
                }

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp.getOrNull());
                }

                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String groovyTaskWithSetPropertyOption(String optionType) {
        """
            public class SampleTask extends DefaultTask {
                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                final SetProperty<$optionType> myProp = project.objects.setProperty($optionType)

                @TaskAction
                public void renderOptionValue() {
                    println("Value of myProp: " + myProp.getOrNull())
                }

                private static enum TestEnum {
                    OPT_1, OPT_2, OPT_3
                }
            }
        """
    }

    String taskWithMultipleOptions() {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;

            public class SampleTask extends DefaultTask {
                private String prop1;
                private Boolean prop2;
                private String prop3;
                
                @Option(option = "prop1", description = "Configures command line option 'prop1'.")
                public void setProp1(String prop1) {
                    this.prop1 = prop1;
                }
                
                @Option(option = "prop2", description = "Configures command line option 'prop2'.")
                public void setProp2(Boolean prop2) {
                    this.prop2 = prop2;
                }
                
                @Option(option = "prop3", description = "Configures command line option 'prop3'.")
                public void setProp3(String prop3) {
                    this.prop3 = prop3;
                }
                
                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of prop1: " + prop1);
                    System.out.println("Value of prop2: " + prop2);
                    System.out.println("Value of prop3: " + prop3);
                }
            }
        """
    }
}
