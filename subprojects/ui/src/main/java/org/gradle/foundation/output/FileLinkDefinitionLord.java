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

import org.gradle.foundation.output.definitions.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class holds on FileLinkDefinitions used for searching output.
 */
public class FileLinkDefinitionLord {
    private List<String> extensions = new ArrayList<String>();

    //these are definitions where the file is between known tokens.
    private Map<Pattern, FileLinkDefinition> complexFileLinkDefinitions = new LinkedHashMap<Pattern, FileLinkDefinition>();

    //these are definitions where we only try to match based on the file extension.
    private Map<Pattern, FileLinkDefinition> extensionFileLinkDefinitions = new LinkedHashMap<Pattern, FileLinkDefinition>();

    private Pattern combinedSearchPattern;  //search pattern consisting of all of our sub search patterns

    public FileLinkDefinitionLord() {
        //this is where we define what files we find.

        //add all the file extension definitions
        addFileExtension(".java", ":");     //the colon handles compile errors with line numbers
        addFileExtension(".groovy", ":");
        addFileExtension(".gradle", ":");
        addFileExtension(".xml", ":"); //I don't think I've ever seen an xml or html file specified with a line number delimiter, but we'll try it anyway
        addFileExtension(".html", ":");
        addFileExtension(".htm", ":");

        //now add the more complex ones
        addPrefixedFileLink("Ant Compiler Error", "[ant:javac]", ".java", ":");       //handles java compiler errors
        addPrefixedFileLink("Compiler Warning", "Note:", ".java", null);               //handles java compiler warnings such as deprecated APIs
        addCustomComplexFileLink(new OptionalLineNumberFileLinkDefinition("Build File Errors", "Build file '", ".gradle", "line:"));       //handles errors in a gradle build file
        addPrefixedFileLink("Ant Checkstyle Error/Warning", "[ant:checkstyle]", ".java", ":"); //handles checkstyle errors/warnings
        addPrefixedFileLink("Checkstyle Error (report xml)", "See the report at", ".xml", null);   //handles checkstyle errors. Links to the report xml file.
        addPrefixedFileLink("Codenarc Error", "See the report at", ".html", null);      //handles Codenarc errors. Links to the report file.
        addCustomComplexFileLink(new TestReportFileLinkDefinition());
    }

    /**
     * Call this to add file extensions to look for in the output. This assumes the file path is the first thing on the line.
     *
     * @param extension the file extension
     * @param lineNumberDelimiter optional delimiter text for line number. Whatever is after this will be assumed to be a line number. We'll only parse the numbers after this so there can be other
     * stuff after the line number. Pass in null to ignore.
     */
    public void addFileExtension(String extension, String lineNumberDelimiter) {
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        extension = extension.toLowerCase();
        if (extensions.contains(extension)) //don't add extensions already added
        {
            return;
        }

        extensions.add(extension);

        String name = extension + " Files";
        ExtensionFileLinkDefinition linkDefinition = new ExtensionFileLinkDefinition(name, extension, lineNumberDelimiter);
        addToMap(extensionFileLinkDefinitions, linkDefinition);
    }

    /**
     * Creates a file link definition to find file paths in the output that have a known prefix and extension. It also allows for an optional line number after a delimiter. This is useful if you know
     * a certain message always precedes a file path.
     *
     * @param name the name of this file link definition. Used by tests mostly.
     * @param prefix the text that is before the file path. It should be enough to make it fairly unique
     * @param extension the expected file extension. If we don't find this extension, we do not consider the text a file's path. If there are multiple extensions, you'll have to add multiples of
     * these.
     * @param lineNumberDelimiter optional delimiter text for line number. Whatever is after this will be assumed to be a line number. We'll only parse the numbers after this so there can be other
     * stuff after the line number. Pass in null to ignore.
     */
    public void addPrefixedFileLink(String name, String prefix, String extension, String lineNumberDelimiter) {
        PrefixedFileLinkDefinition linkDefinition = new PrefixedFileLinkDefinition(name, prefix, extension, lineNumberDelimiter);
        addToMap(complexFileLinkDefinitions, linkDefinition);
    }

    private void addCustomComplexFileLink(FileLinkDefinition fileLinkDefinition) {
        addToMap(complexFileLinkDefinitions, fileLinkDefinition);
    }

    private void addToMap(Map<Pattern, FileLinkDefinition> destinationMap, FileLinkDefinition fileLinkDefinition) {
        //if you change anything, we'll destroy our combined search pattern. This will recreate it with the
        //latest settings when its next asked for.
        combinedSearchPattern = null;

        String searchExpression = fileLinkDefinition.getSearchExpression();
        Pattern pattern = Pattern.compile(searchExpression, getSearchPatternFlags());
        destinationMap.put(pattern, fileLinkDefinition);
    }

    /**
     * @return a list of known file extensions that are searched for in the output.
     */
    public List<String> getFileExtensions() {
        return Collections.unmodifiableList(extensions);
    }

    /**
     * @return a list of our FileLinkDefinitions
     */
    public List<FileLinkDefinition> getFileLinkDefinitions() {
        List<FileLinkDefinition> fileLinkDefinitions = new ArrayList<FileLinkDefinition>();

        fileLinkDefinitions.addAll(complexFileLinkDefinitions.values());
        fileLinkDefinitions.addAll(extensionFileLinkDefinitions.values());

        return Collections.unmodifiableList(fileLinkDefinitions);
    }

    private int getSearchPatternFlags() {
        return Pattern.CASE_INSENSITIVE;
    }

    /**
     * This returns the FileLinkDefinition whose search pattern 'matches' (as in 'finds', not 'equals') the specified text. The tricky thing here is that multiple FileLinkDefinitions can match the
     * text. To assist this we've done two things: we first try to match it with the complex patterns (the ones that try to match prefixed and suffixed text around a file's path), then if we don't
     * find one, we'll match it with a simple extension FileLinkDefinitions. The other thing is that we search the definitions in order. This means the order in which the FileLinkDefinitions are added
     * can be important. Add the more definitive ones first.
     *
     * @param text the text to use to find a match.
     * @return a FileLinkDefinition that matches the text
     */
    public FileLinkDefinition getMatchingFileLinkDefinition(String text) {
        FileLinkDefinition fileLinkDefinition = getMatchingFileLinkDefinition(text, complexFileLinkDefinitions);
        if (fileLinkDefinition == null) {
            fileLinkDefinition = getMatchingFileLinkDefinition(text, extensionFileLinkDefinitions);
        }

        return fileLinkDefinition;
    }

    private static FileLinkDefinition getMatchingFileLinkDefinition(String text, Map<Pattern, FileLinkDefinition> map) {
        Iterator<Pattern> iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            Pattern pattern = iterator.next();
            Matcher matcher = pattern.matcher(text);
            if (matcher.find(0)) {
                return map.get(pattern);
            }
        }

        return null;
    }

    public Pattern getSearchPattern() {
        if (combinedSearchPattern == null) {
            //only build it if we need to.
            combinedSearchPattern = buildSearchPattern();
        }

        return combinedSearchPattern;
    }

    /**
     * This iterates through all the FileLinkDefinitions and builds one giant single RegEx search pattern. This is more efficient than using multiple search patterns.
     */
    private Pattern buildSearchPattern() {
        StringBuilder criteria = new StringBuilder();
        Iterator<FileLinkDefinition> iterator = getFileLinkDefinitions().iterator();
        while (iterator.hasNext()) {
            FileLinkDefinition fileLinkDefinition = iterator.next();
            String searchExpression = fileLinkDefinition.getSearchExpression();

            criteria.append("(").append(searchExpression).append(")");

            if (iterator.hasNext()) {
                criteria.append("|");
            }
        }

        return Pattern.compile(criteria.toString(), getSearchPatternFlags());
    }
}
