/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.docs.asciidoctor;

import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.asciidoctor.converter.ConverterFor;
import org.asciidoctor.converter.StringConverter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts the user manual to Markdown, one {@code .md} file per page.
 * <p>
 * Asciidoctor parses the document as it does for HTML, so includes, attributes, {@link SampleIncludeProcessor},
 * ids and cross-references all behave the same. Cross-references point at {@code .md} files because Asciidoctor
 * derives {@code outfilesuffix} from this converter.
 * <p>
 * Passthrough blocks ({@code ++++}) are dropped, as their raw HTML only works on the HTML site.
 * <p>
 * Multi-language samples keep the Kotlin DSL variant and drop the Groovy DSL one, unless there is no Kotlin variant.
 * Code blocks keep their language, e.g. {@code ```kotlin} or {@code ```groovy}.
 */
@ConverterFor(value = MarkdownConverter.BACKEND, suffix = ".md")
public class MarkdownConverter extends StringConverter {

    public static final String BACKEND = "markdown";

    private static final String KOTLIN = "kotlin";
    private static final String GROOVY = "groovy";
    private static final String FENCE = "```";

    /**
     * Callout markers at the end of a code line, e.g. {@code <1>}, {@code // <1>}, {@code # <1>} or {@code <!--1-->}.
     */
    private static final Pattern CALLOUTS = Pattern.compile(
        "[ \\t]*(?:(?://|#|--|;;) ?)?(?:<!--(?:\\d+|\\.)-->|<(?:\\d+|\\.)>)(?=(?:[ \\t]*(?:(?://|#|--|;;) ?)?(?:<!--(?:\\d+|\\.)-->|<(?:\\d+|\\.)>))*$)",
        Pattern.MULTILINE
    );
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\n{3,}");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);

    private final Map<String, String> footnotes = new LinkedHashMap<>();

    /**
     * How many documents are being converted. Nested documents ({@code a|} table cells) reuse this converter
     * in the middle of their parent's body.
     */
    private int documentDepth;

    public MarkdownConverter(String backend, Map<String, Object> opts) {
        super(backend, opts);
    }

    @Override
    public String convert(ContentNode node, @Nullable String transform, Map<Object, Object> opts) {
        String name = transform != null ? transform : String.valueOf(node.getNodeName());
        switch (name) {
            case "document":
            case "embedded":
                return document((Document) node);
            case "preamble":
                return content(node) + "\n\n";
            case "pass":
                // Raw HTML (++++ blocks): embeds, scripts, redirects and widgets only work on the HTML site
                return "";
            case "section":
                return section((Section) node);
            case "floating_title":
                return anchor(node) + "#".repeat(((StructuralNode) node).getLevel() + 1) + " " + ((StructuralNode) node).getTitle() + "\n\n";
            case "paragraph":
                return anchor(node) + title(node) + content(node) + "\n\n";
            case "listing":
                return anchor(node) + title(node) + fence(source((Block) node), language(node));
            case "literal":
                return anchor(node) + title(node) + fence(source((Block) node), "");
            case "admonition":
                return admonition(node);
            case "ulist":
                return anchor(node) + title(node) + list((org.asciidoctor.ast.List) node, false) + "\n";
            case "olist":
            case "colist":
                return anchor(node) + title(node) + list((org.asciidoctor.ast.List) node, true) + "\n";
            case "dlist":
                return anchor(node) + title(node) + descriptionList((DescriptionList) node) + "\n";
            case "table":
                return anchor(node) + title(node) + table((Table) node) + "\n";
            case "image":
                return anchor(node) + "![" + attribute(node, "alt") + "](" + imagePath(node, attribute(node, "target")) + ")\n\n";
            case "example":
            case "open":
            case "sidebar":
                return container((StructuralNode) node);
            case "quote":
            case "verse":
                return quote(content(node).trim()) + "\n\n";
            case "thematic_break":
                return "---\n\n";
            case "page_break":
            case "toc":
            case "inline_callout":
                return "";
            case "inline_quoted":
                return inlineQuoted((PhraseNode) node);
            case "inline_anchor":
                return inlineAnchor((PhraseNode) node);
            case "inline_footnote":
                return inlineFootnote((PhraseNode) node);
            case "inline_break":
                return ((PhraseNode) node).getText() + "  ";
            case "inline_image":
                return "![" + attribute(node, "alt") + "](" + imagePath(node, ((PhraseNode) node).getTarget()) + ")";
            case "inline_indexterm":
                return "visible".equals(type((PhraseNode) node)) ? ((PhraseNode) node).getText() : "";
            case "inline_kbd":
                return "`" + ((PhraseNode) node).getText() + "`";
            case "inline_button":
            case "inline_menu":
                return "**" + ((PhraseNode) node).getText() + "**";
            default:
                // Anything else (audio, video, stem, ...) falls back to its content
                if (node instanceof PhraseNode) {
                    return ((PhraseNode) node).getText();
                }
                return node instanceof StructuralNode ? content(node) + "\n\n" : "";
        }
    }

    private String document(Document document) {
        if (documentDepth > 0) {
            // A nested document is part of its parent, which collects the footnotes and post-processes the page
            return content(document);
        }
        footnotes.clear();
        String body;
        documentDepth++;
        try {
            body = content(document);
        } finally {
            documentDepth--;
        }
        StringBuilder out = new StringBuilder();
        out.append(anchor(document));
        String doctitle = document.getDoctitle();
        if (doctitle != null) {
            out.append("# ").append(doctitle).append("\n\n");
        }
        out.append(body);
        if (!footnotes.isEmpty()) {
            out.append("\n");
            footnotes.forEach((index, text) -> out.append("[^").append(index).append("]: ").append(text).append("\n"));
        }
        String markdown = decodeEntities(out.toString());
        return EXCESS_BLANK_LINES.matcher(markdown).replaceAll("\n\n").trim() + "\n";
    }

    private String section(Section section) {
        return anchor(section) + "#".repeat(section.getLevel() + 1) + " " + section.getTitle() + "\n\n" + content(section);
    }

    private String admonition(ContentNode node) {
        String label = attribute(node, "textlabel");
        if (label.isEmpty()) {
            label = attribute(node, "name").toUpperCase(Locale.ROOT);
        }
        return anchor(node) + "> **" + label + "**\n>\n" + quote(content(node).trim()) + "\n\n";
    }

    private String container(StructuralNode node) {
        String dsl = dsl(node);
        if (GROOVY.equals(dsl) && hasKotlinSibling(node)) {
            return "";
        }
        return anchor(node) + title(node) + content(node) + "\n\n";
    }

    private String list(org.asciidoctor.ast.List list, boolean ordered) {
        StringBuilder out = new StringBuilder();
        int index = 1;
        for (StructuralNode child : list.getItems()) {
            ListItem item = (ListItem) child;
            String marker = ordered ? index + "." : "*";
            index++;
            List<String> parts = new ArrayList<>();
            if (item.hasText()) {
                parts.add(item.getText());
            }
            String nested = content(item).trim();
            if (!nested.isEmpty()) {
                parts.add(nested);
            }
            String body = String.join("\n\n", parts);
            out.append(marker).append(' ').append(indent(body, " ".repeat(marker.length() + 1)).trim()).append('\n');
        }
        return out.toString();
    }

    private String descriptionList(DescriptionList list) {
        StringBuilder out = new StringBuilder();
        for (DescriptionListEntry entry : list.getItems()) {
            String terms = entry.getTerms().stream().map(ListItem::getText).collect(Collectors.joining(", "));
            out.append("* **").append(terms).append("**");
            ListItem description = entry.getDescription();
            if (description != null) {
                List<String> parts = new ArrayList<>();
                if (description.hasText()) {
                    parts.add(description.getText());
                }
                String nested = content(description).trim();
                if (!nested.isEmpty()) {
                    parts.add(nested);
                }
                if (!parts.isEmpty()) {
                    out.append(": ").append(indent(String.join("\n\n", parts), "  ").trim());
                }
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String table(Table table) {
        int columns = table.getColumns().size();
        List<String> header = table.getHeader().isEmpty()
            ? Collections.nCopies(columns, " ")
            : cells(table.getHeader().get(0));
        StringBuilder out = new StringBuilder();
        out.append("| ").append(String.join(" | ", header)).append(" |\n");
        out.append("| ").append(String.join(" | ", Collections.nCopies(columns, "---"))).append(" |\n");
        List<Row> rows = new ArrayList<>(table.getBody());
        rows.addAll(table.getFooter());
        for (Row row : rows) {
            out.append("| ").append(String.join(" | ", cells(row))).append(" |\n");
        }
        return out.toString();
    }

    private static List<String> cells(Row row) {
        List<String> cells = new ArrayList<>();
        for (Cell cell : row.getCells()) {
            String text = "asciidoc".equals(cell.getStyle()) ? String.valueOf(cell.getContent()) : cell.getText();
            cells.add(text.trim().replaceAll("\\s*\n\\s*", " ").replace("|", "\\|"));
        }
        return cells;
    }

    private static String inlineQuoted(PhraseNode node) {
        String text = node.getText();
        switch (type(node)) {
            case "strong":
                return "**" + text + "**";
            case "emphasis":
                return "*" + text + "*";
            case "monospaced":
                return "`" + text + "`";
            default:
                // Gradle's label macros (since-gradle-label, incubating-label, ...) render HTML badges
                return text.startsWith("<span") ? "*" + TAG.matcher(text).replaceAll("") + "*" : text;
        }
    }

    private static String inlineAnchor(PhraseNode node) {
        switch (type(node)) {
            case "xref": {
                String text = node.getText();
                if (text == null || text.isEmpty()) {
                    text = xrefText(node);
                }
                return "[" + text + "](" + node.getTarget() + ")";
            }
            case "link":
                return "[" + node.getText() + "](" + node.getTarget() + ")";
            default:
                String id = node.getId();
                return id != null ? "<a id=\"" + id + "\"></a>" : "";
        }
    }

    /**
     * Text for a cross-reference without explicit text, resolved like the html5 converter:
     * the path of another page, else the reftext or title of the target, else {@code [refid]}.
     * Unlike html5, it ignores {@code xrefstyle}, which the user manual does not set.
     */
    private static String xrefText(PhraseNode node) {
        String path = attribute(node, "path");
        if (!path.isEmpty()) {
            return path;
        }
        String refid = attribute(node, "refid");
        Object ref = node.getDocument().getCatalog().getRefs().get(refid);
        if (ref instanceof ContentNode) {
            String reftext = ((ContentNode) ref).getReftext();
            if (reftext != null && !reftext.isEmpty()) {
                return reftext;
            }
        }
        if (ref instanceof StructuralNode) {
            String title = ((StructuralNode) ref).getTitle();
            if (title != null && !title.isEmpty()) {
                return title;
            }
        }
        return "[" + refid + "]";
    }

    private String inlineFootnote(PhraseNode node) {
        String index = attribute(node, "index");
        if (index.isEmpty()) {
            return "";
        }
        // A named footnote referenced again ("xref") reuses the text of its first occurrence
        if (!"xref".equals(node.getType())) {
            footnotes.putIfAbsent(index, node.getText());
        }
        return "[^" + index + "]";
    }

    /**
     * {@code kotlin} or {@code groovy} for the variants of a multi-language sample, otherwise {@code null}.
     */
    private static @Nullable String dsl(ContentNode node) {
        if (node.hasRole("lang-kotlin")) {
            return KOTLIN;
        }
        if (node.hasRole("lang-groovy")) {
            return GROOVY;
        }
        String dir = attribute(node, "dir");
        if (dir.endsWith("/" + KOTLIN)) {
            return KOTLIN;
        }
        if (dir.endsWith("/" + GROOVY)) {
            return GROOVY;
        }
        // Hand-written variants: [.multi-language-sample] around a [source,kotlin] or [source,groovy] block
        if (node.hasRole("multi-language-sample") && node instanceof StructuralNode) {
            for (StructuralNode child : ((StructuralNode) node).getBlocks()) {
                String language = attribute(child, "language");
                if (KOTLIN.equals(language) || GROOVY.equals(language)) {
                    return language;
                }
            }
        }
        return null;
    }

    private static boolean hasKotlinSibling(StructuralNode node) {
        ContentNode parent = node.getParent();
        if (!(parent instanceof StructuralNode)) {
            return false;
        }
        for (StructuralNode sibling : ((StructuralNode) parent).getBlocks()) {
            if (sibling != node && KOTLIN.equals(dsl(sibling))) {
                return true;
            }
        }
        return false;
    }

    private static String language(ContentNode node) {
        return attribute(node, "language");
    }

    /**
     * The verbatim source of a code block, with attributes resolved if the block enables them, e.g. {@code subs="attributes"}.
     */
    private static String source(Block block) {
        if (!block.isSubstitutionEnabled("attributes")) {
            return block.getSource();
        }
        // Substitutions also convert callouts, which leaves the whitespace that preceded them
        return TRAILING_WHITESPACE.matcher(content(block)).replaceAll("");
    }

    private static String fence(String source, String language) {
        String code = CALLOUTS.matcher(source).replaceAll("");
        return FENCE + language + "\n" + code + "\n" + FENCE + "\n\n";
    }

    private static String anchor(ContentNode node) {
        String id = node.getId();
        return id != null ? "<a id=\"" + id + "\"></a>\n" : "";
    }

    private static String title(ContentNode node) {
        if (node instanceof StructuralNode) {
            String title = ((StructuralNode) node).getTitle();
            if (title != null && !title.isEmpty()) {
                return "**" + title + "**\n\n";
            }
        }
        return "";
    }

    private static String content(ContentNode node) {
        if (node instanceof StructuralNode) {
            Object content = ((StructuralNode) node).getContent();
            return content != null ? content.toString() : "";
        }
        return "";
    }

    /**
     * The type of an inline node. Nodes created by extensions, e.g. Gradle's label macros, may not have one.
     */
    private static String type(PhraseNode node) {
        String type = node.getType();
        return type != null ? type : "";
    }

    private static String attribute(ContentNode node, String name) {
        Object value = node.getAttribute(name);
        return value != null ? value.toString() : "";
    }

    private static String imagePath(ContentNode node, String target) {
        if (target.contains("://") || target.startsWith("/")) {
            return target;
        }
        String imagesDir = attribute(node.getDocument(), "imagesdir");
        return imagesDir.isEmpty() ? target : imagesDir + "/" + target;
    }

    private static String indent(String text, String padding) {
        return text.lines().map(line -> line.isEmpty() ? line : padding + line).collect(Collectors.joining("\n"));
    }

    private static String quote(String text) {
        return text.lines().map(line -> line.isEmpty() ? ">" : "> " + line).collect(Collectors.joining("\n"));
    }

    /**
     * Asciidoctor escapes text for HTML. Markdown readers expect plain characters.
     */
    static String decodeEntities(String text) {
        Matcher matcher = NUMERIC_ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            int codePoint = code.startsWith("x") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
            // Zero-width spaces are only there to help HTML line breaking
            String replacement = codePoint == 0x200B ? "" : new String(Character.toChars(codePoint));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&");
    }
}
