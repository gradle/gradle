/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

class ManagedNestedBeanJavaInterOpIntegrationTest extends AbstractNestedBeanJavaInterOpIntegrationTest {
    def setup() {
        pluginDir.file("src/main/java/SomeTask.java") << """
            import ${DefaultTask.name};
            import ${TaskAction.name};
            import ${Nested.name};

            public abstract class SomeTask extends DefaultTask {
                @Nested
                public abstract Params getParams();

                @TaskAction
                public void run() {
                    System.out.println("flag = " + getParams().getFlag().get());
                }
            }
        """
    }
}
