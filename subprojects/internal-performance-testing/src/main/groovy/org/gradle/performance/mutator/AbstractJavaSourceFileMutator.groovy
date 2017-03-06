/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.mutator

abstract class AbstractJavaSourceFileMutator extends AbstractFileChangeMutator {

    AbstractJavaSourceFileMutator(String sourceFilePath) {
        super(sourceFilePath)
        if (!sourceFilePath.endsWith(".java")) {
            throw new IllegalArgumentException("Can only modify Java source files")
        }
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        int lastOpeningPos = text.lastIndexOf("{")
        int insertPos = text.indexOf("}", lastOpeningPos)
        boolean isClassClosing = insertPos == text.lastIndexOf("}")
        if (insertPos < 0 || isClassClosing) {
            throw new IllegalArgumentException("Cannot parse source file $sourceFile to apply changes")
        }
        applyChangeAt(text, insertPos)
    }

    protected abstract void applyChangeAt(StringBuilder text, int lastMethodEndPos)
}
