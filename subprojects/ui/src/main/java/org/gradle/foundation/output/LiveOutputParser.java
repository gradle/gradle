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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This is a special type of OutputParser. It handles tracking live output. The unique thing about live output is that we're not guaranteed to get whole lines. Also, we don't want to parse parts that
 * have already been parsed. This holds onto the output until a newline is reached, then parses it. It also tracks the overall index into the output (even though its only parsing a part of it).
 */
public class LiveOutputParser {
    private OutputParser parser;
    private List<FileLink> fileLinks = new ArrayList<FileLink>();
    private StringBuilder totalTextToParse = new StringBuilder();
    private int lastNewline;

    public LiveOutputParser(FileLinkDefinitionLord fileLinkDefinitionLord, boolean verifyFileExists) {
        parser = new OutputParser(fileLinkDefinitionLord, verifyFileExists);
    }

    /**
     * Removes all text and FileLinks. This is so you can use this on new text
     */
    public void reset() {
        //recreate the parser because its possible the definitions have changed.
        boolean verifyFileExists = parser.isVerifyFileExists();
        FileLinkDefinitionLord fileLinkDefinitionLord = parser.getFileLinkDefinitionLord();
        parser = new OutputParser(fileLinkDefinitionLord, verifyFileExists);

        lastNewline = 0;
        totalTextToParse.setLength(0);
        fileLinks.clear();
    }

    public List<FileLink> appendText(String text) {
        int oldTotalSize = totalTextToParse.length();

        totalTextToParse.append(text);
        int indexOfNewline = text.lastIndexOf('\n');
        if (indexOfNewline == -1) {
            return Collections.emptyList();  //nothing to search yet
        }

        //compensate the index for the total size
        indexOfNewline += oldTotalSize;

        //get everything between the last newline and this one
        String textToParse = totalTextToParse.substring(lastNewline, indexOfNewline);

        //search it
        List<FileLink> subFileLinks = parser.parseText(textToParse);

        //for each FileLink we have, we have to correct it's offsets because we didn't search from the beginning.
        Iterator<FileLink> iterator = subFileLinks.iterator();
        while (iterator.hasNext()) {
            FileLink fileLink = iterator.next();
            fileLink.move(lastNewline);
        }

        fileLinks.addAll(subFileLinks);

        lastNewline = indexOfNewline;

        return subFileLinks;
    }

    public List<FileLink> getFileLinks() {
        return Collections.unmodifiableList(fileLinks);
    }

    /**
     * This gets the fileLink at the specified index in the text.
     *
     * @param index the index into the overall text.
     * @return a FileLink if one exists at the index. null if not.
     */
    public FileLink getFileLink(int index) {
        if (index < 0 || index >= totalTextToParse.length()) {
            return null;
        }

        Iterator<FileLink> iterator = fileLinks.iterator();

        while (iterator.hasNext()) {
            FileLink fileLink = iterator.next();
            if (fileLink.getStartingIndex() <= index && fileLink.getEndingIndex() >= index) {
                return fileLink;
            }
        }

        return null;
    }

    public String getText() {
        return totalTextToParse.toString();
    }

    /**
     * Returns the previous file link relative to the caret location. This will cycle around and get the last one if you're before the first link.
     */
    public FileLink getPreviousFileLink(int caretLocation) {
        if (fileLinks.isEmpty()) {
            return null;
        }

        //walk them in reverse order
        for (int index = fileLinks.size() - 1; index >= 0; index--) {
            FileLink fileLink = fileLinks.get(index);
            if (fileLink.getEndingIndex() < caretLocation) {
                return fileLink;
            }
        }

        return fileLinks.get(fileLinks.size() - 1);
    }

    /**
     * Returns the next file link relative to the caret location. This will cycle around and get the first one if you're after the last link.
     */
    public FileLink getNextFileLink(int caretLocation) {
        if (fileLinks.isEmpty()) {
            return null;
        }

        Iterator<FileLink> iterator = fileLinks.iterator();
        while (iterator.hasNext()) {
            FileLink fileLink = iterator.next();
            if (fileLink.getStartingIndex() > caretLocation) {
                return fileLink;
            }
        }

        return fileLinks.get(0);
    }
}
