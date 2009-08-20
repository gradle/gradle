/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.tasks.bundling;

/**
 * @author Hans Dockter
 */
public class LongFile {
    public static final LongFile TRUNCATE = new LongFile("truncate");
    public static final LongFile WARN = new LongFile("warn");
    public static final LongFile GNU = new LongFile("gnu");
    public static final LongFile OMIT = new LongFile("omit");
    public static final LongFile FAIL = new LongFile("fail");

    private final String antValue; 

    private LongFile(String name) {
        antValue = name;
    }

    public String getAntValue() {
        return antValue;
    }
}
