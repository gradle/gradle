package org.gradle.build

import java.util.regex.Pattern
import java.util.regex.Matcher

public class LatexParser {
    def parse(String text, TagHandler handler) {
        Pattern commentPattern = Pattern.compile('%.*$', Pattern.MULTILINE)
        Matcher matcher = commentPattern.matcher(text)
        text = matcher.replaceAll('\n')
        text = text.replaceAll('\\\\_', '_').replaceAll('\\\\#', '#')

        Pattern directivePattern = Pattern.compile('\\\\(\\w+)')
        matcher = directivePattern.matcher(text)
        int lastMatch = 0
        while (matcher.find(lastMatch)) {
            if (matcher.start() > lastMatch) {
                handler.text(text.substring(lastMatch, matcher.start()))
            }
            List params = []
            int pos = matcher.end()
            while (pos < text.length()) {
                if (text[pos] == '[' || text[pos] == '{') {
                    int end = findEnd(text, pos)
                    params.add(text.substring(pos + 1, end - 1))
                    pos = end
                } else {
                    break
                }
            }
            lastMatch = pos
            handler.directive(matcher.group(1), matcher.group(), params)
        }
        if (lastMatch < text.length()) {
            handler.text(text.substring(lastMatch, text.length()))
        }
    }

    private int findEnd(String text, int start) {
        char open = text[start]
        char close = open == '[' ? ']' : '}'
        int depth = 1
        int pos = start + 1
        for (; depth != 0 && pos < text.length(); pos++) {
            if (text[pos] == open) {
                depth++
            }
            if (text[pos] == close) {
                depth--
            }
        }
        return pos
    }
}