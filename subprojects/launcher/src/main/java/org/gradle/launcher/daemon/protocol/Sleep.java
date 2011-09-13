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

package org.gradle.launcher.daemon.protocol;

/**
 * Command that sleeps, pretending a build that does something useful for some time. Used in tests.
 *
 * @author: Szczepan Faber, created at: 9/7/11
 */
public class Sleep extends Command {

    private final int millis;

    public Sleep(int millis) {
        super(null);
        this.millis = millis;
    }

    public void run() {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            //ignore
        }
    }
}