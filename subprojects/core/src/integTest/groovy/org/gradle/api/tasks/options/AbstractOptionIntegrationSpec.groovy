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

    String taskWithSinglePropertyOption(String propertyType, String optionType) {
        String methodName = propertyType.substring(0, 1).toLowerCase() + propertyType.substring(1)

        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.Internal;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.provider.SetProperty;

            public class SampleTask extends DefaultTask {
                private final $propertyType<$optionType> myProp = getProject().getObjects().${methodName}(${optionType}.class);

                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public $propertyType<$optionType> getMyProp() {
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

    String groovyTaskWithSinglePropertyOption(String propertyType, String optionType) {
        String methodName = propertyType.substring(0, 1).toLowerCase() + propertyType.substring(1)

        """
            public class SampleTask extends DefaultTask {
                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                final $propertyType<$optionType> myProp = project.objects.$methodName($optionType)

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

    String taskWithUnparameterizedPropertyOption(String propertyType, String methodName) {
        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.tasks.Internal;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.options.Option;
            import org.gradle.api.provider.Property;

            public class SampleTask extends DefaultTask {
                private final $propertyType myProp = getProject().getObjects().${methodName}();

                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                public $propertyType getMyProp() {
                    return myProp;
                }

                @TaskAction
                public void renderOptionValue() {
                    System.out.println("Value of myProp: " + myProp.getOrNull());
                }
            }
        """
    }

    String groovyTaskWithUnparameterizedPropertyOption(String propertyType, String methodName) {
        """
            public class SampleTask extends DefaultTask {
                @Internal
                @Option(option = "myProp", description = "Configures command line option 'myProp'.")
                final $propertyType myProp = project.objects.$methodName()

                @TaskAction
                public void renderOptionValue() {
                    println("Value of myProp: " + myProp.getOrNull())
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
