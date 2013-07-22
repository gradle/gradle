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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.userinterface.swing.generic.OutputUILord;
import org.gradle.openapi.external.ui.OutputObserverVersion1;
import org.gradle.openapi.external.ui.OutputUILordVersion1;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper to shield version changes in OutputUILord from an external user of gradle open API.
 */
public class OutputUILordWrapper implements OutputUILordVersion1 {

    private OutputUILord outputUILord;
    private Map<OutputObserverVersion1, OutputObserverWrapper> outputObserverMap = new HashMap<OutputObserverVersion1, OutputObserverWrapper>();

    public OutputUILordWrapper(OutputUILord outputUILord) {
        this.outputUILord = outputUILord;
    }

    public void setOutputTextFont(Font font) {
        outputUILord.setOutputTextFont(font);
    }

    public Font getOutputTextFont() {
        return outputUILord.getOutputTextFont();
    }

    public void addFileExtension(String extension, String lineNumberDelimiter) {
        outputUILord.getFileLinkDefinitionLord().addFileExtension(extension, lineNumberDelimiter);
    }

    public void addPrefixedFileLink(String name, String prefix, String extension, String lineNumberDelimiter) {
        outputUILord.getFileLinkDefinitionLord().addPrefixedFileLink(name, prefix, extension, lineNumberDelimiter);
    }

    public List<String> getFileExtensions() {
        return outputUILord.getFileLinkDefinitionLord().getFileExtensions();
    }

    public void addOutputObserver(OutputObserverVersion1 observer) {
        //to avoid versioning conflicts, we have to wrap the observer. This means we have to track the observers.
        OutputObserverWrapper wrapper = new OutputObserverWrapper(observer);
        outputObserverMap.put(observer, wrapper);

        outputUILord.addOutputObserver(wrapper, false);
    }

    public void removeOutputObserver(OutputObserverVersion1 observer) {
        OutputObserverWrapper wrapper = outputObserverMap.remove(observer);
        if (wrapper != null) {
            outputUILord.removeOutputObserver(wrapper);
        }
    }

    /*
    This re-executes the last execution command (ignores refresh commands).
    This is potentially useful for IDEs to hook into (hotkey to execute last command).
     */
    public void reExecuteLastCommand() {
        outputUILord.reExecuteLastCommand();
    }
}
