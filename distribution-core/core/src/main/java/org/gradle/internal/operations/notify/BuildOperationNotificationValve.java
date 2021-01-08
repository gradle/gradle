/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.notify;

/**
 * Controls an instance of build operation notifications.
 *
 * This is required as the build operation notification machinery is effectively session scoped,
 * but we need to allow, external (i.e. non ListenerManager), listeners per build.
 *
 * Furthermore, the actual lifecycle is not something that we currently model with the service registries.
 * The notification listener is effectively of cross build tree scope, which doesn't exist.
 * This is because GradleBuild uses a discrete tree (which is intended to change later).
 */
public interface BuildOperationNotificationValve {

    void start();

    void stop();

}
