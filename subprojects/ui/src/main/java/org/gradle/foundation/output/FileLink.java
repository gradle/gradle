/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.foundation.output;

import org.gradle.foundation.output.definitions.FileLinkDefinition;

import java.io.File;

/**
 * This represents a link to a file inside gradle's output. This is so the gradle UI/plugins can open the file. This is useful for a user when gradle displays a build error, test failure or compile
 * error.
 */
public class FileLink {
    private File file;
    private int lineNumber;
    private int startingIndex;
    private int endingIndex;
    private FileLinkDefinition matchingDefinition;  //useful for debugging.

    public FileLink(File file, int startingIndex, int endingIndex, int lineNumber, FileLinkDefinition matchingDefinition) {
        this.file = file;
        this.startingIndex = startingIndex;
        this.endingIndex = endingIndex;
        this.lineNumber = lineNumber;
        this.matchingDefinition = matchingDefinition;
    }

    public FileLink(File file, int startingIndex, int endingIndex, int lineNumber) {
        this(file, startingIndex, endingIndex, lineNumber, null);
    }

    @Override
    public String toString() {
        return "file='" + file + "' startingIndex=" + startingIndex + " endingIndex=" + endingIndex + " line: " + lineNumber + (matchingDefinition != null ? (" definition: " + matchingDefinition
                .getName()) : "");
    }

    public int getLength() {
        return endingIndex - startingIndex;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * @return the line number into the file. May be -1 if not specified
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * @return the index into the source text where this FileLink begins.
     */
    public int getStartingIndex() {
        return startingIndex;
    }

    /**
     * @return the index into the source text where this FileLink ends.
     */
    public int getEndingIndex() {
        return endingIndex;
    }

    /**
     * This moves the starting and ending index by the specified amount. This is useful if you're searching within a portion of larger text. This corrects the original indices.
     *
     * @param amountToMove how much to move it.
     */
    /*package*/ void move(int amountToMove) {
        startingIndex += amountToMove;
        endingIndex += amountToMove;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FileLink)) {
            return false;
        }

        FileLink otherFileLink = (FileLink) obj;
        return otherFileLink.endingIndex == endingIndex && otherFileLink.startingIndex == startingIndex && otherFileLink.lineNumber == lineNumber && otherFileLink.file.equals(file);
        //we do NOT want to compare the FileLinkDefinition. These aren't set usually for tests and we don't have easy access to them anyway.
    }

    @Override
    public int hashCode() {
        return endingIndex ^ startingIndex ^ lineNumber ^ file.hashCode();
    }
}
