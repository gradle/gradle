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
import org.gradle.internal.operations.notify.BuildOperationNotificationListener
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar
import org.gradle.internal.operations.notify.BuildOperationProgressNotification
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import org.gradle.test.fixtures.file.TestDirectoryProvider

import java.lang.reflect.InvocationTargetException

/**
 * Implicitly tests that the build emits usable build operation notifications,
 * with the listener subscribing as the root project is loaded.
 *
 * This exercises the store-and-replay behaviour of the notification dispatcher
 * that allows a listener to observe all of the notifications from the very start of the build.
 *
 * This fixture reflectively exercises the details/results objects of the notifications,
 * ensuring that they are “usable”.
 */
class BuildOperationNotificationsFixture {

    public static final String FIXTURE_SCRIPT_NAME = "injectBuildOpFixture.gradle"
    private final File initScript

    BuildOperationNotificationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        this.initScript = projectDir.testDirectory.file(FIXTURE_SCRIPT_NAME)
        this.initScript.text = injectNotificationListenerBuildLogic()
        executer.beforeExecute {
            it.usingInitScript(this.initScript)
        }
    }

    static String injectNotificationListenerBuildLogic() {
        """
            rootProject {
                if(gradle.isRootBuild()) {
                    def listener = new BuildOperationNotificationsEvaluationListener()
                    def registrar = project.services.get($BuildOperationNotificationListenerRegistrar.name)
                    registrar.register(listener)
                }
            }

            $EVALUATION_LISTENER_SOURCE
        """
    }

    public static final String EVALUATION_LISTENER_SOURCE = """
        class BuildOperationNotificationsEvaluationListener implements $BuildOperationNotificationListener.name {
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

                private void verify(Object obj, String suffix = null) {
                    if (obj.getClass().getName().equals("org.gradle.internal.operations.OperationProgressDetails")) {
                        // This progress notification is emitted by Gradle itself.
                        return;
                    }
                    def matchingInterfaces = findPublicInterfaces(obj, suffix)
                    if (matchingInterfaces.empty) {
                        if (suffix == null) {
                            throw new org.gradle.api.GradleException("No interface implemented by \${obj.getClass()}.")
                        } else {
                            throw new org.gradle.api.GradleException("No interface with suffix '\$suffix' found.")
                        }
                    }

                    matchingInterfaces.each { i ->
                        invokeMethods(obj, i)
                    }
                }

                List<Class<?>> findPublicInterfaces(Object object, String suffix) {
                    def interfaces = object.getClass().interfaces

                    if (suffix == null) {
                        interfaces
                    } else {
                        interfaces.findAll { it.name.endsWith(suffix) }
                    }
                }

                void invokeMethods(Object object, Class<?> clazz) {
                    for (def method : clazz.methods) {
                        try {
                            if (method.parameterTypes.size() == 0) {
                                method.invoke(object)
                            }
                        } catch (any) {
                            def cause = any
                            if (any instanceof $InvocationTargetException.name) {
                                cause = any.cause
                            }
                            throw new RuntimeException("Failed to invoke \$method.name() of \$clazz.name", cause)
                        }
                    }
                }
            }
    """
}
