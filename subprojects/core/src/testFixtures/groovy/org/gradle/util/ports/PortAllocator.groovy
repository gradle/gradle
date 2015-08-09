/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.util.ports


interface PortAllocator {
    public static final int MIN_PRIVATE_PORT = 49152
    public static final int MAX_PRIVATE_PORT = 65535

    /**
     * Assign and reserve a port
     * @return the port assigned
     */
    int assignPort()

    /**
     * Release a previously assigned port
     * @param port
     */
    void releasePort(int port)
}
