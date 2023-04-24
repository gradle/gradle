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
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.Requires
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestPrecondition
import org.gradle.util.internal.RedirectStdOutAndErr

import static org.gradle.internal.classloader.ClasspathUtil.getClasspathForClass

class ToolingApiClassLoaderProvider {
    private static final Map<String, ClassLoader> TEST_CLASS_LOADERS = [:]
    private static final GradleDistribution CURRENT_GRADLE = new UnderDevelopmentGradleDistribution(IntegrationTestBuildContext.INSTANCE)

    static ClassLoader getToolingApiClassLoader(ToolingApiDistribution toolingApi, Class<?> target) {
        def testClassPath = [ToolingApiSpecification, target]
            .collect { getClasspathForClass(it) }

        testClassPath.addAll(collectAdditionalClasspath(toolingApi, target))

        getTestClassLoader(toolingApi, testClassPath)
    }

    private static List<File> collectAdditionalClasspath(ToolingApiDistribution toolingApi, Class<?> target) {
        target.annotations.findAll { it instanceof ToolingApiAdditionalClasspath }.collectMany { annotation ->
            (annotation as ToolingApiAdditionalClasspath).value()
                .getDeclaredConstructor()
                .newInstance()
                .additionalClasspathFor(toolingApi, CURRENT_GRADLE)
        }
    }

    private static ClassLoader getTestClassLoader(ToolingApiDistribution toolingApi, List<File> testClasspath) {
        synchronized (ToolingApiClassLoaderProvider) {
            def classLoader = TEST_CLASS_LOADERS.get(toolingApi.version.version)
            if (!classLoader) {
                classLoader = createTestClassLoader(toolingApi, testClasspath)
                TEST_CLASS_LOADERS.put(toolingApi.version.version, classLoader)
            }
            return classLoader
        }
    }

    private static ClassLoader createTestClassLoader(ToolingApiDistribution toolingApi, List<File> testClassPath) {
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
        sharedSpec.allowPackage('org.gradle.nativeplatform.fixtures')
        sharedSpec.allowPackage('org.gradle.language.fixtures')
        sharedSpec.allowPackage('org.gradle.workers.fixtures')
        sharedSpec.allowPackage('org.gradle.launcher.daemon.testing')
        sharedSpec.allowPackage('org.gradle.tooling')
        sharedSpec.allowPackage('org.gradle.kotlin.dsl.tooling.builders')
        sharedSpec.allowClass(OperatingSystem)
        sharedSpec.allowClass(Requires)
        sharedSpec.allowClass(TestPrecondition)
        sharedSpec.allowClass(TargetGradleVersion)
        sharedSpec.allowClass(ToolingApiVersion)
        sharedSpec.allowClass(TeeOutputStream)
        sharedSpec.allowClass(ClassLoaderFixture)
        def sharedClassLoader = classLoaderFactory.createFilteringClassLoader(Thread.currentThread().getContextClassLoader(), sharedSpec)

        def parentClassLoader = new MultiParentClassLoader(toolingApi.classLoader, sharedClassLoader)

        return new VisitableURLClassLoader("test", parentClassLoader, testClassPath.collect { it.toURI().toURL() })
    }
}
