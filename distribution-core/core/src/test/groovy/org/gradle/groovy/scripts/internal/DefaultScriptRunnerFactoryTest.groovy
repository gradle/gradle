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
package org.gradle.groovy.scripts.internal

import org.gradle.api.GradleScriptException
import org.gradle.groovy.scripts.Script
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.groovy.scripts.ScriptRunner
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.logging.StandardOutputCapture
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

class DefaultScriptRunnerFactoryTest extends Specification {
    private final CompiledScript<? extends Script, Void> compiledScriptMock = Mock()
    private final Script scriptMock = Mock(Script.class)
    private final StandardOutputCapture standardOutputCaptureMock = Mock(StandardOutputCapture.class)
    private final ClassLoader classLoaderDummy = Mock(ClassLoader.class)
    private final ScriptSource scriptSourceDummy = Mock(ScriptSource.class)
    private final ScriptExecutionListener scriptExecutionListenerMock = Mock(ScriptExecutionListener.class)
    private final Instantiator instantiatorMock = Mock(Instantiator.class)
    private final Object target = new Object()
    private final ServiceRegistry scriptServices = Mock(ServiceRegistry.class)
    private final DefaultScriptRunnerFactory factory = new DefaultScriptRunnerFactory(scriptExecutionListenerMock, instantiatorMock)
    
    def doesNotLoadScriptWhenScriptRunnerCreated() {
        when:
        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy)

        then:
        scriptRunner != null
        0 * _._
    }

    def runDoesNothingWhenEmptyScriptIsRun() {
        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy)
        when:
        scriptRunner.run(target, scriptServices)

        then:
        1 * compiledScriptMock.runDoesSomething >> false
    }

    def setsUpAndTearsDownWhenNonEmptyScriptIsRun() {
        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy)

        when:
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader()
        scriptRunner.run(target, scriptServices)
        assert Thread.currentThread().getContextClassLoader() == originalClassLoader

        then:
        1 * compiledScriptMock.loadClass() >> Script
        1 * instantiatorMock.newInstance(Script) >> scriptMock
        1 * scriptMock.setScriptSource(_)
        1 * scriptMock.setContextClassloader(classLoaderDummy)
        _ * scriptMock.standardOutputCapture >> standardOutputCaptureMock
        _ * scriptMock.scriptSource >> scriptSourceDummy
        _ * scriptMock.contextClassloader >> classLoaderDummy

        1 * compiledScriptMock.runDoesSomething >> true
        1 * scriptExecutionListenerMock.onScriptClassLoaded(scriptSourceDummy, Script)
        1 * scriptMock.init(target, scriptServices)
        1 * standardOutputCaptureMock.start()
        1 * scriptMock.run()
        1 * standardOutputCaptureMock.stop()
    }

    def wrapsExecutionExceptionAndRestoresStateWhenScriptFails() {
        final RuntimeException failure = new RuntimeException()

        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy)

        when:
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader()
        try {
            scriptRunner.run(target, scriptServices)
        } finally {
            assert Thread.currentThread().getContextClassLoader() == originalClassLoader
        }

        then:
        def e = thrown(GradleScriptException)
        e.message == "A problem occurred evaluating ${scriptMock}."
        e.cause == failure

        and:
        1 * compiledScriptMock.loadClass() >> Script
        1 * instantiatorMock.newInstance(Script) >> scriptMock
        1 * scriptMock.setScriptSource(_)
        1 * scriptMock.setContextClassloader(classLoaderDummy)
        _ * scriptMock.standardOutputCapture >> standardOutputCaptureMock
        _ * scriptMock.scriptSource >> scriptSourceDummy
        _ * scriptMock.contextClassloader >> classLoaderDummy

        1 * compiledScriptMock.runDoesSomething >> true
        1 * scriptExecutionListenerMock.onScriptClassLoaded(scriptSourceDummy, Script)
        1 * scriptMock.init(target, scriptServices)
        1 * standardOutputCaptureMock.start()
        1 * scriptMock.run() >> { throw failure }
        1 * standardOutputCaptureMock.stop()
    }
}
