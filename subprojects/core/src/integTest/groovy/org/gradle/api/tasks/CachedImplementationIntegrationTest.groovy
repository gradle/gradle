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

package org.gradle.api.tasks

import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

class CachedImplementationIntegrationTest extends AbstractIntegrationSpec {

    def "can use full Java build cache service implementation"() {
        def pluginJar = file("plugin.jar")
        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.packageName = null

        pluginBuilder.java("InMemoryBuildCache.java") << """
            public class InMemoryBuildCache extends $AbstractBuildCache.name {}
        """

        pluginBuilder.java("InMemoryBuildCacheService.java") << """
            import java.io.*;
            import java.util.*;
            import org.gradle.caching.*;
            import org.apache.commons.codec.binary.Base64;

            public class InMemoryBuildCacheService implements BuildCacheServiceFactory<InMemoryBuildCache> {
                @Override
                public BuildCacheService createBuildCacheService(InMemoryBuildCache configuration, Describer describer) {
                    describer.type("in-memory");
                    final Properties data = new Properties();
                    final File cacheFile = new File("cache.bin");
                    if (cacheFile.exists()) {
                        try (InputStream input = new FileInputStream(cacheFile)) {
                            data.load(input);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    return new BuildCacheService() {
                        @Override
                        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                            String value = data.getProperty(key.getHashCode());
                            if (value == null) {
                                return false;
                            }
                            try {
                                byte[] bytes = Base64.decodeBase64(value);
                                reader.readFrom(new ByteArrayInputStream(bytes));
                                return true;
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }

                        @Override
                        public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            try {
                                writer.writeTo(buffer);
                                String string = Base64.encodeBase64String(buffer.toByteArray());
                                data.setProperty(key.getHashCode(), string);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }

                        @Override
                        public void close() throws IOException {
                            try (OutputStream output = new FileOutputStream(cacheFile)) {
                                data.store(output, null);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    };
                }
            }
        """

        pluginBuilder.java("InMemoryBuildCachePlugin.java") << """
            import org.gradle.api.*;
            import org.gradle.api.initialization.*;
            import org.gradle.caching.configuration.*;

            public class InMemoryBuildCachePlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings settings) {
                    settings.getBuildCache().registerBuildCacheService(InMemoryBuildCache.class, InMemoryBuildCacheService.class);
                    settings.buildCache(new Action<BuildCacheConfiguration>() {
                        @Override
                        public void execute(BuildCacheConfiguration config) {
                            config.getLocal().setEnabled(false);
                            config.remote(InMemoryBuildCache.class, new Action<InMemoryBuildCache>() {
                                @Override
                                public void execute(InMemoryBuildCache config) {
                                    config.setPush(true);
                                }
                            });
                        }
                    });
                }
            }
        """

        pluginBuilder.publishTo(executer, pluginJar, """
            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-codec:commons-codec:1.10"
            }
        """)

        file("build.gradle") << """
            apply plugin: "java"
        """

        file("src/main/java/Hello.java") << """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World!");
                }
            }
        """

        settingsFile << """
            buildscript {
                repositories {
                    ${mavenCentralRepository()}
                }
                dependencies {
                    classpath "commons-codec:commons-codec:1.10"
                    classpath files("$pluginJar.name")
                }
            }

            apply plugin: InMemoryBuildCachePlugin
        """

        when:
        executer.withBuildCacheEnabled()
        succeeds "compileJava", "--info"

        then:
        executed ":compileJava"

        expect:
        succeeds "clean"

        when:
        executer.withBuildCacheEnabled()
        succeeds "compileJava"

        then:
        skipped ":compileJava"
    }
}
