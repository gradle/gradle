/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.project.antbuilder

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.time.CountdownTimer
import org.gradle.internal.time.Time
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

class AntBuilderMemoryLeakTest extends Specification {

    @Shared
    private ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get())

    @Shared
    private ClassPathRegistry registry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))

    @Shared
    private DefaultClassLoaderFactory classLoaderFactory = new DefaultClassLoaderFactory()

    def "should release cache when cleanup is called"() {
        classLoaderFactory = new DefaultClassLoaderFactory()
        def builder = new DefaultIsolatedAntBuilder(registry, classLoaderFactory, moduleRegistry)

        when:
        builder.withClasspath([new File('foo')]).execute {
            // do something
        }

        then:
        builder.classLoaderCache.size() == 1

        when:
        builder.classLoaderCache.stop()

        then:
        builder.classLoaderCache.isEmpty()

        cleanup:
        builder?.stop()
    }

    @Ignore("Test doesn't fail fast enough")
    def "should release cache under memory pressure"() {
        given:
        def builder = new DefaultIsolatedAntBuilder(registry, classLoaderFactory)
        Class[] classes = new Class[1]

        when:
        int i = 0
        // time out after 10 minutes
        CountdownTimer timer = Time.startCountdownTimer(10, TimeUnit.MINUTES)
        try {
            while (!timer.hasExpired()) {
                builder.withClasspath([new File("foo$i")]).execute {

                }

                classes[classes.length - 1] = Proxy.getProxyClass(classLoaderFactory.createIsolatedClassLoader("test", []), Serializable)
                4.times {
                    // exponential grow to make it fail faster
                    Class[] dup = new Class[classes.length * 2]
                    System.arraycopy(classes, 0, dup, 0, classes.length)
                    System.arraycopy(classes, 0, dup, classes.length, classes.length)
                    classes = dup
                }
                i++
            }
        } catch (OutOfMemoryError e) {
            classes = []
            // we need to give some time for the GC to complete
            sleep(1000)
        }

        then:
        assert i > 1
        assert classes.length == 0
        builder.classLoaderCache.empty || builder.classLoaderCache.size() < i - 1

        cleanup:
        builder.stop()
    }
}
