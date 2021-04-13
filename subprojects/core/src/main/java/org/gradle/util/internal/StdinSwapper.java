/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal;

import org.gradle.api.Action;
import org.gradle.internal.Factory;

import java.io.InputStream;

public class StdinSwapper extends Swapper<InputStream> {

    public StdinSwapper() {
        super(
            new Factory<InputStream>() {
                @Override
                public InputStream create() {
                    return System.in;
                }
            },
            new Action<InputStream>() {
                @Override
                public void execute(InputStream newValue) {
                    System.setIn(newValue);
                }
            }
        );
    }
}
