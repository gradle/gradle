/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.Action
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.exec.DaemonUsageSuggestingBuildActionExecuter
import org.gradle.testing.internal.util.RetryRule
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.Requires
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestPrecondition

trait ToolingApiClasspathProvider {
    ClassLoader getTestClassLoader(
        Map<String, ClassLoader> cache,
        ToolingApiDistribution toolingApi,
        List<File> testClasspath,
        Action<? super FilteringClassLoader> classpathConfigurer) {
        synchronized(ToolingApiClasspathProvider) {
            def classLoader = cache.get(toolingApi.version.version)
            if (!classLoader) {
                classLoader = createTestClassLoader(toolingApi, classpathConfigurer, testClasspath)
                cache.put(toolingApi.version.version, classLoader)
            }
            return classLoader
        }
    }

    private ClassLoader createTestClassLoader(ToolingApiDistribution toolingApi, Action<? super FilteringClassLoader> classpathConfigurer, List<File> testClassPath) {
        def classLoaderFactory = new DefaultClassLoaderFactory()

        def sharedClassLoader = classLoaderFactory.createFilteringClassLoader(getClass().classLoader)
        sharedClassLoader.allowPackage('org.junit')
        sharedClassLoader.allowPackage('org.hamcrest')
        sharedClassLoader.allowPackage('junit.framework')
        sharedClassLoader.allowPackage('groovy')
        sharedClassLoader.allowPackage('org.codehaus.groovy')
        sharedClassLoader.allowPackage('spock')
        sharedClassLoader.allowPackage('org.spockframework')
        sharedClassLoader.allowClass(SetSystemProperties)
        sharedClassLoader.allowClass(RedirectStdOutAndErr)
        sharedClassLoader.allowPackage('org.gradle.integtests.fixtures')
        sharedClassLoader.allowPackage('org.gradle.play.integtest.fixtures')
        sharedClassLoader.allowPackage('org.gradle.test.fixtures')
        sharedClassLoader.allowPackage('org.gradle.launcher.daemon.testing')
        sharedClassLoader.allowClass(OperatingSystem)
        sharedClassLoader.allowClass(Requires)
        sharedClassLoader.allowClass(TestPrecondition)
        sharedClassLoader.allowClass(TargetGradleVersion)
        sharedClassLoader.allowClass(ToolingApiVersion)
        sharedClassLoader.allowClass(DaemonUsageSuggestingBuildActionExecuter)
        sharedClassLoader.allowClass(TeeOutputStream)
        sharedClassLoader.allowClass(RetryRule)
        classpathConfigurer.execute(sharedClassLoader)

        def parentClassLoader = new MultiParentClassLoader(toolingApi.classLoader, sharedClassLoader)

        return new MutableURLClassLoader(parentClassLoader, testClassPath.collect { it.toURI().toURL() })
    }
}
