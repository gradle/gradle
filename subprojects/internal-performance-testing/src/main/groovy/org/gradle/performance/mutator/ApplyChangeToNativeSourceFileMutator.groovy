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

class ApplyChangeToNativeSourceFileMutator extends AbstractFileChangeMutator {

    ApplyChangeToNativeSourceFileMutator(String sourceFilePath) {
        super(sourceFilePath)
        if (!sourceFilePath.endsWith('.cpp') && !sourceFilePath.endsWith('.h')) {
            throw new IllegalArgumentException('Can only modify C++ source or header files')
        }
    }

    @Override
    protected void applyChangeTo(StringBuilder text) {
        if (sourceFilePath.endsWith('.cpp')) {
            applyChangeAt(text, text.length())
        } else {
            int insertPos = text.lastIndexOf("#endif")
            if (insertPos < 0) {
                throw new IllegalArgumentException("Cannot parse header file " + sourceFilePath + " to apply changes")
            }
            applyHeaderChangeAt(text, insertPos)
        }
    }

    private void applyChangeAt(StringBuilder text, int insertPos) {
        text.insert(insertPos, "\nint _m" + uniqueText + " () { }")
    }

    private void applyHeaderChangeAt(StringBuilder text, int insertPos) {
        text.insert(insertPos, "int _m" + uniqueText + "();\n")
    }

}
