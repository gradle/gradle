/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.util.shutdown;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Tom Eyckmans
 */
public class ShutdownHookActionRegister {
    private static final ShutdownHookActionRegister INSTANCE = new ShutdownHookActionRegister();

    private final List<ShutdownHookAction> shutdownHookActions;

    private ShutdownHookActionRegister()
    {
        shutdownHookActions = new CopyOnWriteArrayList<ShutdownHookAction>();
    }

    public static void addShutdownHookAction(ShutdownHookAction shutdownHookAction)
    {
        if (shutdownHookAction != null) {
            INSTANCE.shutdownHookActions.add(shutdownHookAction);
        }
    }

    public static void removeShutdownHookAction(ShutdownHookAction shutdownHookAction) {
        if (shutdownHookAction != null) {
            INSTANCE.shutdownHookActions.remove(shutdownHookAction);
        }
    }

    static List<ShutdownHookAction> getSHutHookActions()
    {
        return Collections.unmodifiableList(INSTANCE.shutdownHookActions);
    }
}
