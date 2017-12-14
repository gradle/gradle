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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification
import org.gradle.internal.operations.notify.BuildOperationNotificationListener2
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar
import org.gradle.internal.operations.notify.BuildOperationProgressNotification
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import org.gradle.test.fixtures.file.TestDirectoryProvider

class BuildOperationNotificationsFixture {

    private final File initScript

    BuildOperationNotificationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        this.initScript = projectDir.testDirectory.file("injectBuildOpFixture.gradle")
        this.initScript.text = injectNotificationListenerBuildLogic()
        executer.beforeExecute {
            it.usingInitScript(this.initScript)
        }
        executer.afterExecute {
            it.reset()
        }
    }

    String injectNotificationListenerBuildLogic() {
        return """
        rootProject {
            if(gradle.getParent() == null) {
                def listener = new BuildOperationNotificationsEvaluationListener();
                def registrar = project.services.get($BuildOperationNotificationListenerRegistrar.name)
                registrar.register(listener)
            }
        } 
        
        class BuildOperationNotificationsEvaluationListener implements $BuildOperationNotificationListener2.name {
            private final static Logger LOGGER = Logging.getLogger(BuildOperationNotificationsEvaluationListener)
            
            @Override
            void started($BuildOperationStartedNotification.name notification) {
                triggerGetters(notification.notificationOperationDetails)
            }
    
            @Override
            void progress($BuildOperationProgressNotification.name notification) {
                triggerGetters(notification.notificationOperationProgressDetails)
            }
    
            @Override
            void finished($BuildOperationFinishedNotification.name notification) {
                triggerGetters(notification.notificationOperationResult)
            }
            
            
            void triggerGetters(Object object) {
                def clazz = object.getClass()
    
                Class<?>[] interfaces = clazz.getInterfaces()
                def found = false
                for (Class<?> i : interfaces) {
                    def interfaceName = i.name
                    if (interfaceName.endsWith('Details') || interfaceName.endsWith('Result')) {
                        found = true
                        LOGGER.info "   Checking \$interfaceName"   
                        def methods = i.getMethods()
                        for (java.lang.reflect.Method method : methods) {
                            LOGGER.info "       \${method.name} - \${method.invoke(object)}"
                        }
                    }
    
                }
                if(!found) {
                    throw new GradleException("Havn't found BuildOperationNotification Details/Result interface to inspect")
                }
                
            }
        }
        """
    }

    static class BuildOperationNotificationsEvaluationListener implements BuildOperationNotificationListener2 {

        @Override
        void started(BuildOperationStartedNotification notification) {
            triggerGetters(notification.notificationOperationDetails)
            notification.notificationOperationDetails.
                println "BuildOperationNotificationsEvaluationListener.started"
        }

        @Override
        void progress(BuildOperationProgressNotification notification) {
            println "BuildOperationNotificationsEvaluationListener.progress"
        }

        @Override
        void finished(BuildOperationFinishedNotification notification) {
            println "BuildOperationNotificationsEvaluationListener.finished"
        }

        void triggerGetters(Object object) {
            def clazz = object.getClass()

            Class<?>[] interfaces = clazz.getInterfaces()
            for (Class<?> i : interfaces) {
                def interfaceName = i.name
                if (interfaceName.endsWith('Details') || interfaceName.endsWith('Result')) {
                    def methods = i.getMethods()
                    for (java.lang.reflect.Method method : methods) {
                        println method.name
                    }
                }

            }
        }
    }

}
