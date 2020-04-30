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

package org.gradle.api.provider

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class ManagedPropertyJavaInterOpIntegrationTest extends AbstractPropertyJavaInterOpIntegrationTest {
    def setup() {
        pluginDir.file("src/main/java/SomeTask.java") << """
            import ${DefaultTask.name};
            import ${Property.name};
            import ${ListProperty.name};
            import ${SetProperty.name};
            import ${MapProperty.name};
            import ${TaskAction.name};
            import ${Internal.name};

            public abstract class SomeTask extends DefaultTask {
                @Internal
                public abstract Property<Boolean> getFlag();

                @Internal
                public abstract Property<String> getMessage();

                @Internal
                public abstract Property<Double> getNumber();

                @Internal
                public abstract ListProperty<Integer> getList();

                @Internal
                public abstract SetProperty<Integer> getSet();

                @Internal
                public abstract MapProperty<Integer, Boolean> getMap();

                @TaskAction
                public void run() {
                    System.out.println("flag = " + getFlag().get());
                    System.out.println("message = " + getMessage().get());
                    System.out.println("number = " + getNumber().get());
                    System.out.println("list = " + getList().get());
                    System.out.println("set = " + getSet().get());
                    System.out.println("map = " + getMap().get());
                }
            }
        """
    }
}
