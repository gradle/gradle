/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util

import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.api.Task
import org.gradle.api.internal.DefaultInstantiatorFactory
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.InstantiatorFactory
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.model.DefaultObjectFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.groovy.scripts.DefaultScript
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testfixtures.internal.NativeServicesTestFixture

import java.rmi.server.UID

import static org.gradle.api.internal.FeaturePreviews.Feature.GRADLE_METADATA

class TestUtil {
    public static final Closure TEST_CLOSURE = {}

    private final File rootDir

    private TestUtil(File rootDir) {
        NativeServicesTestFixture.initialize()
        this.rootDir = rootDir
    }

    static InstantiatorFactory instantiatorFactory() {
        return new DefaultInstantiatorFactory(new TestCrossBuildInMemoryCacheFactory())
    }

    static ObjectFactory objectFactory() {
        return objFactory(TestFiles.resolver())
    }

    static ObjectFactory objectFactory(TestFile baseDir) {
        return objFactory(TestFiles.resolver(baseDir))
    }

    private static ObjectFactory objFactory(FileResolver fileResolver) {
        DefaultServiceRegistry services = new DefaultServiceRegistry()
        services.add(ProviderFactory, new DefaultProviderFactory())
        return new DefaultObjectFactory(instantiatorFactory().injectAndDecorate(services), NamedObjectInstantiator.INSTANCE, fileResolver, TestFiles.directoryFileTreeFactory(), new DefaultFilePropertyFactory(fileResolver))
    }


    static NamedObjectInstantiator objectInstantiator() {
        return NamedObjectInstantiator.INSTANCE
    }

    static FeaturePreviews featurePreviews(boolean gradleMetadataEnabled = false) {
        def previews = new FeaturePreviews()
        if (gradleMetadataEnabled) {
            previews.enableFeature(GRADLE_METADATA)
        }
        return previews
    }

    static TestUtil create(File rootDir) {
        return new TestUtil(rootDir)
    }

    static TestUtil create(TestDirectoryProvider testDirectoryProvider) {
        return new TestUtil(testDirectoryProvider.testDirectory)
    }

    public <T extends Task> T task(Class<T> type) {
        return createTask(type, createRootProject(this.rootDir))
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project) {
        return createTask(type, project, 'name')
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project, String name) {
        return project.services.get(TaskInstantiator).create(name, type)
    }

    static ProjectBuilder builder(File rootDir) {
        return ProjectBuilder.builder().withProjectDir(rootDir)
    }

    static ProjectBuilder builder(TestDirectoryProvider temporaryFolder) {
        return builder(temporaryFolder.testDirectory)
    }

    ProjectInternal rootProject() {
        createRootProject(rootDir)
    }

    static ProjectInternal createRootProject(File rootDir) {
        return ProjectBuilder
            .builder()
            .withProjectDir(rootDir)
            .build()
    }

    static ProjectInternal createChildProject(ProjectInternal parent, String name, File projectDir = null) {
        return ProjectBuilder
            .builder()
            .withName(name)
            .withParent(parent)
            .withProjectDir(projectDir)
            .build()
    }

    static groovy.lang.Script createScript(String code) {
        new GroovyShell().parse(code)
    }

    static Object call(String text, Object... params) {
        toClosure(text).call(*params)
    }

    static Closure toClosure(String text) {
        return new GroovyShell().evaluate("return " + text)
    }

    static Closure toClosure(ScriptSource source) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.setScriptBaseClass(TestScript.getName())

        GroovyShell shell = new GroovyShell(configuration)
        Script script = shell.parse(source.resource.text)
        script.setScriptSource(source)
        return script.run()
    }

    static Closure toClosure(TestClosure closure) {
        return { param -> closure.call(param) }
    }

    static Closure returns(Object value) {
        return { value }
    }

    static Closure createSetterClosure(String name, String value) {
        return {
            "set$name"(value)
        }
    }

    static String createUniqueId() {
        return new UID().toString()
    }


}


interface TestClosure {
    Object call(Object param);
}

abstract class TestScript extends DefaultScript {
}
