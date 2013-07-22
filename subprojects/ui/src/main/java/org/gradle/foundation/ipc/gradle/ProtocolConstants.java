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
package org.gradle.foundation.ipc.gradle;

/**
 * Constants related to interprocess communication between gradle and the gradle UI.
 */
public class ProtocolConstants {
    //these are the message types we'll receive from the client.
    public static final String EXECUTION_COMPLETED_TYPE = "ExecutionCompleted";
    public static final String NUMBER_OF_TASKS_TO_EXECUTE = "NumberOfTasksToExecute";
    public static final String TASK_STARTED_TYPE = "TaskStarted";
    public static final String TASK_COMPLETE_TYPE = "TaskComplete";
    public static final String LIVE_OUTPUT_TYPE = "LiveOutput";

    public static final String HANDSHAKE_TYPE = "connected";
    public static final String HANDSHAKE_SERVER = "server-reply";
    public static final String HANDSHAKE_CLIENT = "client-reply";

    public static final String PORT_NUMBER_SYSTEM_PROPERTY = "PortNumber";

    public static final String TASK_LIST_COMPLETED_SUCCESSFULLY_TYPE = "TaskListCompletedSuccessfully";
    public static final String TASK_LIST_COMPLETED_WITH_ERRORS_TYPE = "TaskListCompletedWithErrors";

    public static final String EXITING = "exiting";

    public static final String KILL = "kill";
}
