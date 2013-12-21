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
package org.gradle.openapi.external.ui;

import java.awt.*;
import java.util.List;

/**
 * Provides access to aspects of gradle's output
 * @deprecated No replacement
 */
@Deprecated
public interface OutputUILordVersion1 {

    public void setOutputTextFont(Font font);

    public Font getOutputTextFont();

    /**
     * Call this to add file extensions to look for in the output. The files will be highlighted and are clickable by the user. This results in AlternateUIInteractionVersion1.editFile or openFile
     * being called. This assumes the file path is the first thing on the line.
     *
     * @param extension the file extension
     * @param lineNumberDelimiter optional delimiter text for line number. Whatever is after this will be assumed to be a line number. We'll only parse the numbers after this so there can be other
     * stuff after the line number. Pass in null to ignore.
     */
    public void addFileExtension(String extension, String lineNumberDelimiter);

    /**
     * Creates a file link definition to find file paths in the output that have a known prefix and extension. The files will be highlighted and are clickable by the user. This results in
     * AlternateUIInteractionVersion1.editFile or openFile being called. It also allows for an optional line number after a delimiter. This is useful if you know a certain message always precedes a
     * file path.
     *
     * @param name the name of this file link definition. Used by tests mostly.
     * @param prefix the text that is before the file path. It should be enough to make it fairly unique
     * @param extension the expected file extension. If we don't find this extension, we do not consider the text a file's path. If there are multiple extensions, you'll have to add multiples of
     * these.
     * @param lineNumberDelimiter optional delimiter text for line number. Whatever is after this will be assumed to be a line number. We'll only parse the numbers after this so there can be other
     * stuff after the line number. Pass in null to ignore.
     */
    public void addPrefixedFileLink(String name, String prefix, String extension, String lineNumberDelimiter);

    /**
     * @return a list of file extensions that are highlighted in the output
     */
    public List<String> getFileExtensions();

    public void addOutputObserver(OutputObserverVersion1 outputObserverVersion1);

    public void removeOutputObserver(OutputObserverVersion1 outputObserverVersion1);

    /*
    This re-executes the last execution command (ignores refresh commands).
    This is potentially useful for IDEs to hook into (hotkey to execute last command).
     */
    public void reExecuteLastCommand();
}
