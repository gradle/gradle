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
                def listener = new BuildOperationNotificationsEvaluationListener(Logging.getLogger(BuildOperationNotificationsEvaluationListener))
                def registrar = project.services.get($BuildOperationNotificationListenerRegistrar.name)
                registrar.register(listener)
            }
        } 
        
        $EVALUATION_LISTENER_SOURCE
        """
    }

    static String EVALUATION_LISTENER_SOURCE = """
        import org.gradle.api.logging.Logger
    
        class BuildOperationNotificationsEvaluationListener implements $BuildOperationNotificationListener2.name {
                private final Logger logger
                
                public BuildOperationNotificationsEvaluationListener(Logger logger) {
                    this.logger = logger
                }
                
                @Override
                void started($BuildOperationStartedNotification.name notification) {
                    verify(notification.getNotificationOperationDetails(), 'Details')
                }
        
                @Override
                void progress($BuildOperationProgressNotification.name notification) {
                    verify(notification.getNotificationOperationProgressDetails())
                }
        
                @Override
                void finished($BuildOperationFinishedNotification.name notification) {
                    verify(notification.getNotificationOperationResult(), 'Result')
                }
                
                private void verify(Object obj, String postFix = null){
                    List<Class<?>> matchingInterfaces = findInterfaces(obj, postFix)
                    if(matchingInterfaces.size() == 0) {
                        if(postFix == null){
                            throw new org.gradle.api.GradleException("No interface implemented by \${obj.getClass()}.")
                        } else {
                            throw new org.gradle.api.GradleException("No interface with postfix '\$postFix' found.")
                        }
                        
                    }
                    
                    matchingInterfaces.each{ i ->
                        triggerInterfaceMethods(obj, i)
                    }
                }
                
                List<Class<?>> findInterfaces(Object object, String interfacePostfix){
                    def clazz = object.getClass()
                    Class<?>[] interfaces = clazz.getInterfaces()
                    
                    if(interfacePostfix == null) {
                        return interfaces 
                    }
                    List<Class<?>> matchingInterfaces = new ArrayList()
                    for (Class<?> i : interfaces) {
                        def interfaceName = i.name
                        if (interfaceName.endsWith(interfacePostfix)) {
                            matchingInterfaces.add(i)
                        }
                    }
                    return matchingInterfaces;
                }
                
                void triggerInterfaceMethods(Object object, Class<?> iF) {
                    logger.info "   Checking \${iF.getName()}"   
                    def methods = iF.getMethods()
                    for (java.lang.reflect.Method method : methods) {
                        logger.info "       \${method.name}() -> \${method.invoke(object)}"
                    }
                }
            }
    """
}
