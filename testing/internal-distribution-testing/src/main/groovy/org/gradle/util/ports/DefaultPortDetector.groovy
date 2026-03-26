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


class DefaultPortDetector implements PortDetector {
    /**
     * Checks to see if a specific port is available.
     *
     * @param port the port to check for availability
     * @return <code>true</code> if the port is available, <code>false</code> otherwise
     */
    public boolean isAvailable(int port) {
        try {
            ServerSocket ss = new ServerSocket(port)
            try {
                ss.setReuseAddress(true)
            } finally {
                ss.close()
            }
            DatagramSocket ds = new DatagramSocket(port)
            try {
                ds.setReuseAddress(true)
            } finally {
                ds.close()
            }
            return true
        } catch (IOException e) {
            return false
        }
    }
}
