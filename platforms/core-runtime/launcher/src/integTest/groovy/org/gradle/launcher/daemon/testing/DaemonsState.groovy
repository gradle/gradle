/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.testing

/**
 * Represents the number of given busy and idle daemons in a registry at a particular time.
 */
class DaemonsState {
    final int busy
    final int idle

    DaemonsState(int busy, int idle) {
        this.busy = Math.max(busy, 0)
        this.idle = Math.max(idle, 0)
    }

    static getWildcardState() {
        new DaemonsState()
    }

    private DaemonsState() {
        this.busy = -1
        this.idle = -1
    }

    String toString() {
        wildcard ? "DaemonsState{*}" : "DaemonsState{busy=$busy,idle=$idle}"
    }

    boolean matches(DaemonsState rhs) {
        wildcard || (this.busy == rhs.busy && this.idle == rhs.idle)
    }

    boolean isWildcard() {
        busy < 0
    }
}
