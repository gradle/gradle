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
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.os.OperatingSystem
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
        Action<? super FilteringClassLoader.Spec> classpathConfigurer) {
        synchronized(ToolingApiClasspathProvider) {
            def classLoader = cache.get(toolingApi.version.version)
            if (!classLoader) {
                classLoader = createTestClassLoader(toolingApi, classpathConfigurer, testClasspath)
                cache.put(toolingApi.version.version, classLoader)
            }
            return classLoader
        }
    }

    private ClassLoader createTestClassLoader(ToolingApiDistribution toolingApi, Action<? super FilteringClassLoader.Spec> classpathConfigurer, List<File> testClassPath) {
        def classLoaderFactory = new DefaultClassLoaderFactory()

        def sharedSpec = new FilteringClassLoader.Spec()
        sharedSpec.allowPackage('org.junit')
        sharedSpec.allowPackage('org.hamcrest')
        sharedSpec.allowPackage('junit.framework')
        sharedSpec.allowPackage('groovy')
        sharedSpec.allowPackage('org.codehaus.groovy')
        sharedSpec.allowPackage('spock')
        sharedSpec.allowPackage('org.spockframework')
        sharedSpec.allowClass(SetSystemProperties)
        sharedSpec.allowClass(RedirectStdOutAndErr)
        sharedSpec.allowPackage('org.gradle.integtests.fixtures')
        sharedSpec.allowPackage('org.gradle.play.integtest.fixtures')
        sharedSpec.allowPackage('org.gradle.plugins.ide.fixtures')
        sharedSpec.allowPackage('org.gradle.test.fixtures')
        sharedSpec.allowPackage('org.gradle.launcher.daemon.testing')
        sharedSpec.allowClass(OperatingSystem)
        sharedSpec.allowClass(Requires)
        sharedSpec.allowClass(TestPrecondition)
        sharedSpec.allowClass(TargetGradleVersion)
        sharedSpec.allowClass(ToolingApiVersion)
        sharedSpec.allowClass(TeeOutputStream)
        sharedSpec.allowClass(RetryRule)
        sharedSpec.allowClass(ClassLoaderFixture)
        classpathConfigurer.execute(sharedSpec)
        def sharedClassLoader = classLoaderFactory.createFilteringClassLoader(getClass().classLoader, sharedSpec)

        def parentClassLoader = new MultiParentClassLoader(toolingApi.classLoader, sharedClassLoader)

        return new VisitableURLClassLoader(parentClassLoader, testClassPath.collect { it.toURI().toURL() })
    }
}
