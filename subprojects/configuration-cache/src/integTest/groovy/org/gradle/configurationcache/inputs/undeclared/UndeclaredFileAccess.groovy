/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.inputs.undeclared

import org.gradle.api.JavaVersion
import org.junit.Assume

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.function.Supplier

abstract class UndeclaredFileAccess extends BuildInputRead {
    final String filePath

    UndeclaredFileAccess(String filePath) {
        this.filePath = filePath
    }

    @Override
    List<String> requiredImports() {
        [
            File.class,
            FileFilter.class,
            FilenameFilter.class,
            Files.class,
            StandardCharsets.class,
            IOException.class,
            Supplier.class,
            FileInputStream.class
        ].collect { it.canonicalName }
    }

    static class FileCheck extends UndeclaredFileAccess {
        private final String checkKind

        FileCheck(String filePath, String checkKind) {
            super(filePath)
            this.checkKind = checkKind
        }

        @Override
        String getKotlinExpression() {
            "File(\"${filePath}\").$checkKind()"
        }

        @Override
        String getGroovyExpression() {
            "new File(\"${filePath}\").$checkKind()"
        }

        @Override
        String getJavaExpression() {
            "new File(\"${filePath}\").$checkKind()"
        }
    }

    static FileCheck fileExists(String filePath) {
        new FileCheck(filePath, "exists")
    }

    static FileCheck fileIsFile(String filePath) {
        new FileCheck(filePath, "isFile")
    }

    static FileCheck fileIsDirectory(String filePath) {
        new FileCheck(filePath, "isDirectory")
    }

    static UndeclaredFileAccess directoryContent(String directoryPath) {
        new UndeclaredFileAccess(directoryPath) {
            @Override
            String getKotlinExpression() {
                "File(\"$directoryPath\").listFiles()"
            }

            @Override
            String getJavaExpression() {
                "new File(\"$directoryPath\").listFiles()"
            }

            @Override
            String getGroovyExpression() {
                "new File(\"$directoryPath\").listFiles()"
            }
        }
    }

    static UndeclaredFileAccess fileInputStreamConstructor(String filePath) {
        new UndeclaredFileAccess(filePath) {
            @Override
            String getKotlinExpression() {
                "FileInputStream(File(\"$filePath\")).close()"
            }

            @Override
            String getJavaExpression() {
                """
                ((Supplier<Object>) () -> {
                    try {
                        new FileInputStream(new File("$filePath")).close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                }).get()"""
            }

            @Override
            String getGroovyExpression() {
                "{ new FileInputStream(new File(\"$filePath\")).close(); null }()"
            }
        }
    }

    static UndeclaredFileAccess directoryContentWithFileFilter(String directoryPath) {
        new UndeclaredFileAccess(directoryPath) {
            @Override
            String getKotlinExpression() {
                "File(\"$directoryPath\").listFiles { f: File -> true }"
            }

            @Override
            String getJavaExpression() {
                "new File(\"$directoryPath\").listFiles((File f) -> true)"
            }

            @Override
            String getGroovyExpression() {
                "new File(\"$directoryPath\").listFiles((FileFilter) (File file) -> true)"
            }
        }
    }

    static UndeclaredFileAccess directoryContentWithFilenameFilter(String directoryPath) {
        new UndeclaredFileAccess(directoryPath) {
            @Override
            String getKotlinExpression() {
                "File(\"$directoryPath\").listFiles { dir: File, f: String -> true }"
            }

            @Override
            String getJavaExpression() {
                "new File(\"$directoryPath\").listFiles((File dir, String f) -> true)"
            }

            @Override
            String getGroovyExpression() {
                "new File(\"$directoryPath\").listFiles((java.io.FilenameFilter) (File dit, String f) -> true)"
            }
        }
    }

    static UndeclaredFileAccess fileText(String filePath) {
        new UndeclaredFileAccess(filePath) {
            @Override
            String getKotlinExpression() {
                "File(\"$filePath\").readText()"
            }

            @Override
            String getJavaExpression() {
                Assume.assumeTrue(JavaVersion.current().isJava11Compatible())
                """
                ((Supplier<String>) () -> {
                    try {
                        return Files.readString(new File("$filePath").toPath());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).get()
                """.stripIndent()
            }

            @Override
            String getGroovyExpression() {
                "new File(\"$filePath\").text"
            }
        }
    }

    static UndeclaredFileAccess fileTextWithEncoding(String filePath) {
        new UndeclaredFileAccess(filePath) {
            @Override
            String getKotlinExpression() {
                "File(\"$filePath\").readText(StandardCharsets.UTF_8)"
            }

            @Override
            String getJavaExpression() {
                Assume.assumeTrue(JavaVersion.current().isJava11Compatible())
                """
                ((Supplier<String>) () -> {
                    try {
                        return Files.readString(new File("$filePath").toPath(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).get()
                """.stripIndent()
            }

            @Override
            String getGroovyExpression() {
                "new File(\"$filePath\").getText('UTF-8')"
            }
        }
    }
}
