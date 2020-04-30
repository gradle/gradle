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


import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class AbstractPropertyJavaInterOpIntegrationTest extends AbstractPropertyLanguageInterOpIntegrationTest {
    def setup() {
        pluginDir.file("build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                api gradleApi()
            }
        """
    }

    @Override
    void pluginSetsValues() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};
            import ${Arrays.name};
            import ${Map.name};
            import ${LinkedHashMap.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("someTask", SomeTask.class, t -> {
                        t.getFlag().set(true);
                        t.getMessage().set("some value");
                        t.getNumber().set(1.23);
                        t.getList().set(Arrays.asList(1, 2));
                        t.getSet().set(Arrays.asList(1, 2));
                        Map<Integer, Boolean> map = new LinkedHashMap<>();
                        map.put(1, true);
                        map.put(2, false);
                        t.getMap().set(map);
                    });
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValuesUsingCallable() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};
            import ${Arrays.name};
            import ${Map.name};
            import ${LinkedHashMap.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("someTask", SomeTask.class, t -> {
                        t.getFlag().set(project.provider(() -> true));
                        t.getMessage().set(project.provider(() -> "some value"));
                        t.getNumber().set(project.provider(() -> 1.23));
                        t.getList().set(project.provider(() -> Arrays.asList(1, 2)));
                        t.getSet().set(project.provider(() -> Arrays.asList(1, 2)));
                        t.getMap().set(project.provider(() -> {
                            Map<Integer, Boolean> map = new LinkedHashMap<>();
                            map.put(1, true);
                            map.put(2, false);
                            return map;
                        }));
                    });
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValuesUsingMappedProvider() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};
            import ${Provider.name};
            import ${Arrays.name};
            import ${Map.name};
            import ${LinkedHashMap.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("someTask", SomeTask.class, t -> {
                        Provider<String> provider = project.provider(() -> "some value");
                        t.getFlag().set(provider.map(s -> !s.isEmpty()));
                        t.getMessage().set(provider.map(s -> s));
                        t.getNumber().set(provider.map(s -> 1.23));
                        t.getList().set(provider.map(s -> Arrays.asList(1, 2)));
                        t.getSet().set(provider.map(s -> Arrays.asList(1, 2)));
                        t.getMap().set(provider.map(s -> {
                            Map<Integer, Boolean> map = new LinkedHashMap<>();
                            map.put(1, true);
                            map.put(2, false);
                            return map;
                        }));
                    });
                }
            }
        """
    }

    @Override
    void pluginDefinesTask() {
        pluginDir.file("src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("someTask", SomeTask.class);
                }
            }
        """
    }
}
