/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.docs.dsl.docbook;

import gradlebuild.docs.dsl.source.model.ClassMetaData;
import gradlebuild.docs.dsl.source.model.MethodMetaData;
import gradlebuild.docs.dsl.source.model.PropertyMetaData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Generates a synthetic DOM element that is structurally identical to the per-class DSL XML files,
 * driven entirely from {@link ClassMetaData}. This allows classes without hand-maintained XML files
 * to participate in DSL documentation generation.
 */
public class ClassDocXmlGenerator {

    private static final Pattern GETTER_NAME = Pattern.compile("(get|is)\\p{javaUpperCase}.*");
    private static final Pattern SETTER_NAME = Pattern.compile("set\\p{javaUpperCase}.*");

    /**
     * Generates a DOM element matching the structure of the per-class XML files:
     * <pre>
     * &lt;section&gt;
     *   &lt;section&gt;&lt;title&gt;Properties&lt;/title&gt;
     *     &lt;table&gt;
     *       &lt;thead&gt;&lt;tr&gt;&lt;td&gt;Name&lt;/td&gt;&lt;/tr&gt;&lt;/thead&gt;
     *       &lt;tr&gt;&lt;td&gt;propName&lt;/td&gt;&lt;/tr&gt;
     *     &lt;/table&gt;
     *   &lt;/section&gt;
     *   &lt;section&gt;&lt;title&gt;Methods&lt;/title&gt;
     *     &lt;table&gt;
     *       &lt;thead&gt;&lt;tr&gt;&lt;td&gt;Name&lt;/td&gt;&lt;/tr&gt;&lt;/thead&gt;
     *       &lt;tr&gt;&lt;td&gt;methodName&lt;/td&gt;&lt;/tr&gt;
     *     &lt;/table&gt;
     *   &lt;/section&gt;
     * &lt;/section&gt;
     * </pre>
     */
    public static Element generate(ClassMetaData classMetaData, Document document) {
        Element root = document.createElement("section");

        // Properties section
        TreeSet<String> propertyNames = new TreeSet<>();
        for (PropertyMetaData property : classMetaData.getDeclaredProperties()) {
            if (property.isDslHidden()) {
                continue;
            }
            if (!hasDescriptionProse(property.getRawCommentText())) {
                continue;
            }
            propertyNames.add(property.getName());
        }

        root.appendChild(buildTableSection(document, "Properties", propertyNames));

        // Methods section — exclude standard getters/setters (already represented as properties).
        // A standard getter has 0 params (get*/is*), a standard setter has 1 param (set*).
        // Parameterized methods like getByName(String) are NOT standard getters and should
        // appear as methods even though the metadata system creates properties from them.
        // A method name is only included if ALL overloads with that name have Javadoc
        // and none are hidden, since ClassDocMethodsBuilder processes all overloads.
        Set<String> propertyMethodNames = collectPropertyMethodNames(classMetaData);
        TreeSet<String> candidateMethodNames = new TreeSet<>();
        Set<String> excludedMethodNames = new TreeSet<>();
        for (MethodMetaData method : classMetaData.getDeclaredMethods()) {
            if (propertyMethodNames.contains(method.getName()) && isStandardAccessor(method)) {
                continue;
            }
            if (method.isDslHidden() || !hasDescriptionProse(method.getRawCommentText())) {
                excludedMethodNames.add(method.getName());
            } else {
                candidateMethodNames.add(method.getName());
            }
        }
        TreeSet<String> methodNames = new TreeSet<>(candidateMethodNames);
        methodNames.removeAll(excludedMethodNames);

        root.appendChild(buildTableSection(document, "Methods", methodNames));

        return root;
    }

    private static Element buildTableSection(Document document, String title, Set<String> names) {
        Element section = document.createElement("section");
        Element titleEl = document.createElement("title");
        titleEl.setTextContent(title);
        section.appendChild(titleEl);

        Element table = document.createElement("table");
        Element thead = document.createElement("thead");
        Element headerRow = document.createElement("tr");
        Element headerCell = document.createElement("td");
        headerCell.setTextContent("Name");
        headerRow.appendChild(headerCell);
        thead.appendChild(headerRow);
        table.appendChild(thead);

        for (String name : names) {
            Element row = document.createElement("tr");
            Element cell = document.createElement("td");
            cell.setTextContent(name);
            row.appendChild(cell);
            table.appendChild(row);
        }

        section.appendChild(table);
        return section;
    }

    /**
     * Checks if the raw Javadoc comment contains meaningful prose description,
     * not just tags like {@code @return}, {@code @param}, etc.
     * Treats {@code {@inheritDoc}} as valid prose since the description is inherited from the parent.
     */
    private static boolean hasDescriptionProse(String rawCommentText) {
        if (rawCommentText == null) {
            return false;
        }
        // Strip leading * from each line (raw Javadoc format)
        String stripped = rawCommentText.replaceAll("(?m)^\\s*\\*\\s?", "");
        // {@inheritDoc} counts as valid prose since the description comes from the parent
        if (stripped.contains("{@inheritDoc}")) {
            return true;
        }
        // Remove inline tags like {@link ...}, {@code ...}
        stripped = stripped.replaceAll("\\{@[^}]*}", "");
        // Remove block tags (@param, @return, @since, @see, etc.) and everything after them
        stripped = stripped.replaceAll("(?m)^@\\w+.*$", "");
        // Check if there's any meaningful text left
        return !stripped.trim().isEmpty();
    }

    private static Set<String> collectPropertyMethodNames(ClassMetaData classMetaData) {
        Set<String> names = new TreeSet<>();
        for (PropertyMetaData property : classMetaData.getDeclaredProperties()) {
            if (property.getGetter() != null) {
                names.add(property.getGetter().getName());
            }
            if (property.getSetter() != null) {
                names.add(property.getSetter().getName());
            }
        }
        return names;
    }

    /**
     * Returns true if the method is a standard JavaBeans accessor:
     * a getter with 0 params or a setter with 1 param.
     * Parameterized methods like getByName(String) are not standard accessors
     * and should appear as methods in the DSL docs.
     */
    private static boolean isStandardAccessor(MethodMetaData method) {
        String name = method.getName();
        int paramCount = method.getParameters().size();
        if (GETTER_NAME.matcher(name).matches()) {
            return paramCount == 0;
        }
        if (SETTER_NAME.matcher(name).matches()) {
            return paramCount == 1;
        }
        return false;
    }
}
