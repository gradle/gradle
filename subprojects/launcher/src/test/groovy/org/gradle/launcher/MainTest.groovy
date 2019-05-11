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
package org.gradle.launcher

import org.gradle.api.Action
import org.gradle.launcher.bootstrap.ExecutionCompleter
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.launcher.cli.CommandLineActionFactory
import org.gradle.launcher.cli.DefaultCommandLineActionFactory
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class MainTest extends Specification {
    
    @Rule final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    
    Action actionImpl 
    
    void action(Closure closure) {
        actionImpl = closure as Action
    }
    
    def actionFactoryImpl
    
    void actionFactory(Closure closure) {
        actionFactoryImpl = new DefaultCommandLineActionFactory() { Action<ExecutionListener> convert(List args) { closure(args) } }
    }
    boolean completedSuccessfully
    boolean completedWithFailure
    Throwable failure
        
    final String[] args = ['arg']
    
    final Main main = new Main() {
        protected ExecutionCompleter createCompleter() {
            [complete: { completedSuccessfully = true }, completeWithFailure: { completedWithFailure = true; failure = it }] as ExecutionCompleter
        }

        protected CommandLineActionFactory createActionFactory() {
            actionFactoryImpl
        }
    }
    

    def setup() {
        actionFactory { actionImpl }
    }
    
    def createsAndExecutesCommandLineAction() {
        given:
        action {}
            
        when:
        main.run()

        then:
        completedSuccessfully
    }

    def reportsActionExecutionFailure() {
        given:
        def thrownFailure = new RuntimeException('broken')
        action { throw thrownFailure }

        when:
        main.run()

        then:
        outputs.stdErr.contains('FAILURE: Build failed with an exception')
        outputs.stdErr.contains('broken')
        completedWithFailure
        failure == thrownFailure
    }

    def reportsActionCreationFailure() {
        given:
        def thrownFailure = new RuntimeException('broken')
        actionFactory { throw thrownFailure }

        when:
        main.run()

        then:
        outputs.stdErr.contains('FAILURE: Build failed with an exception')
        outputs.stdErr.contains('broken')
        completedWithFailure
        failure == thrownFailure
    }
}
