/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.events.test;

import java.io.Serializable;

/**
 * Enumerates possible output streams for {@link TestOutputEvent}.
 *
 * @since 6.0
 */
public enum Destination implements Serializable {

    StdOut(0),
    StdErr(1);

    private int code;

     Destination(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Destination fromCode(int code) {
        for (Destination d : Destination.values()) {
            if (d.code == code) {
                 return d;
            }
        }
        throw new IllegalArgumentException("Cannot find destination with code " + code);
    }
}
