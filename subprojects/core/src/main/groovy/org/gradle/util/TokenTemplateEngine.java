/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util;

import groovy.lang.*;
import groovy.text.Template;
import groovy.text.TemplateEngine;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.io.*;
import java.util.Map;

/**
 * Processes template source files substituting variables and expressions into
 * placeholders in a template source text to produce the desired output.
 *
 * @author sam
 * @author Christian Stein
 * @author Paul King
 * @author Alex Tkachman
 * @author Benjamin Muschko
 */
public class TokenTemplateEngine extends TemplateEngine {
    private boolean verbose;
    private static int counter = 1;

    private GroovyShell groovyShell;

    public TokenTemplateEngine() {
        this(GroovyShell.class.getClassLoader());
    }

    public TokenTemplateEngine(boolean verbose) {
        this(GroovyShell.class.getClassLoader());
        setVerbose(verbose);
    }

    public TokenTemplateEngine(ClassLoader parentLoader) {
        this(new GroovyShell(parentLoader));
    }

    public TokenTemplateEngine(GroovyShell groovyShell) {
        this.groovyShell = groovyShell;
    }

    public Template createTemplate(Reader reader) throws CompilationFailedException, IOException {
        SimpleTemplate template = new SimpleTemplate();
        String script = template.parse(reader);
        if (verbose) {
            System.out.println("\n-- script source --");
            System.out.print(script);
            System.out.println("\n-- script end --\n");
        }
        try {
            template.script = groovyShell.parse(script, "SimpleTemplateScript" + counter++ + ".groovy");
        } catch (Exception e) {
            throw new GroovyRuntimeException("Failed to parse template script (your template may contain an error or be trying to use expressions not currently supported): " + e.getMessage());
        }
        return template;
    }

    /**
     * @param verbose true if you want the engine to display the template source file for debugging purposes
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    private static class SimpleTemplate implements Template {

        protected Script script;

        public Writable make() {
            return make(null);
        }

        public Writable make(final Map map) {
            return new Writable() {
                /**
                 * Write the template document with the set binding applied to the writer.
                 *
                 * @see groovy.lang.Writable#writeTo(java.io.Writer)
                 */
                public Writer writeTo(Writer writer) {
                    Binding binding;
                    if (map == null) {
                        binding = new Binding();
                    } else {
                        binding = new Binding(map);
                    }
                    Script scriptObject = InvokerHelper.createScript(script.getClass(), binding);
                    PrintWriter pw = new PrintWriter(writer);
                    scriptObject.setProperty("out", pw);
                    scriptObject.run();
                    pw.flush();
                    return writer;
                }

                /**
                 * Convert the template and binding into a result String.
                 *
                 * @see java.lang.Object#toString()
                 */
                public String toString() {
                    StringWriter sw = new StringWriter();
                    writeTo(sw);
                    return sw.toString();
                }
            };
        }

        /**
         * Parse the text document looking for ${..} and then call out to the appropriate handler, otherwise copy the text directly
         * into the script while escaping quotes.
         *
         * @param reader a reader for the template text
         * @return the parsed text
         * @throws IOException if something goes wrong
         */
        protected String parse(Reader reader) throws IOException {
            if (!reader.markSupported()) {
                reader = new BufferedReader(reader);
            }
            StringWriter sw = new StringWriter();
            startScript(sw);
            int c;
            while ((c = reader.read()) != -1) {
                if (c == '$') {
                    reader.mark(1);
                    c = reader.read();
                    if (c != '{') {
                        sw.write('\\');
                        sw.write('$');
                        reader.reset();
                    } else {
                        reader.mark(1);
                        sw.write("${");
                        processGSstring(reader, sw);
                    }
                    continue; // at least '$' is consumed ... read next chars.
                }
                if (c == '\"') {
                    sw.write('\\');
                }
                /*
                 * Handle raw new line characters.
                 */
                if (c == '\n' || c == '\r') {
                    if (c == '\r') { // on Windows, "\r\n" is a new line.
                        reader.mark(1);
                        c = reader.read();
                        if (c != '\n') {
                            reader.reset();
                        }
                    }
                    sw.write("\n");
                    continue;
                }
                sw.write(c);
            }
            endScript(sw);
            return sw.toString();
        }

        private void startScript(StringWriter sw) {
            sw.write("out.print(\"\"\"");
        }

        private void endScript(StringWriter sw) {
            sw.write("\"\"\");\n");
            sw.write("\n/* Generated by SimpleTemplateEngine */");
        }

        private void processGSstring(Reader reader, StringWriter sw) throws IOException {
            int c;
            while ((c = reader.read()) != -1) {
                if (c != '\n' && c != '\r') {
                    sw.write(c);
                }
                if (c == '}') {
                    break;
                }
            }
        }
    }
}