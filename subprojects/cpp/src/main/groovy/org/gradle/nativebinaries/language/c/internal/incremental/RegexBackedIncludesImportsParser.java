package org.gradle.nativebinaries.language.c.internal.incremental;

public class RegexBackedIncludesImportsParser extends AbstractRegexBackedIncludesParser{
    private static final String INCLUDE_IMPORT_PATTERN = "#(include|import)\\s+((<[^>]+>)|(\"[^\"]+\"))";

    public RegexBackedIncludesImportsParser() {
        super(INCLUDE_IMPORT_PATTERN, 2);
    }
}
