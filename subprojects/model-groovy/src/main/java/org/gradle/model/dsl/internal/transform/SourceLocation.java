/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.dsl.internal.transform;

public class SourceLocation {
    private final String scriptSourceDescription;
    private final int lineNumber;
    private final int columnNumber;

    public SourceLocation(String scriptSourceDescription, int lineNumber, int columnNumber) {
        this.scriptSourceDescription = scriptSourceDescription;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public String getScriptSourceDescription() {
        return scriptSourceDescription;
    }

    @Override
    public String toString() {
        return String.format("%s line %d, column %d", scriptSourceDescription, lineNumber, columnNumber);
    }
}
