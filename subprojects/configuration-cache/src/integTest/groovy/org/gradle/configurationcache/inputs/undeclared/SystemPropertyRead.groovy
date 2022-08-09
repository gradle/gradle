/*
 * Copyright 2020 the original author or authors.
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

abstract class SystemPropertyRead extends BuildInputRead {
    static SystemPropertyRead systemGetProperty(String name) {
        return new SystemPropertyRead() {
            @Override
            String getKotlinExpression() {
                return "System.getProperty(\"$name\")"
            }
        }
    }

    static SystemPropertyRead systemGetPropertyWithDefault(String name, String defaultValue) {
        return new SystemPropertyRead() {
            @Override
            String getKotlinExpression() {
                return "System.getProperty(\"$name\", \"$defaultValue\")"
            }
        }
    }

    static SystemPropertyRead systemGetPropertiesGet(String name) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "(String)System.getProperties().get(\"$name\")"
            }

            @Override
            String getGroovyExpression() {
                return "System.properties[\"$name\"]"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperties()[\"$name\"]"
            }
        }
    }

    static SystemPropertyRead systemGetPropertiesGetProperty(String name) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "(String)System.getProperties().getProperty(\"$name\")"
            }

            @Override
            String getGroovyExpression() {
                return "System.properties.getProperty(\"$name\")"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperties().getProperty(\"$name\")"
            }
        }
    }

    static SystemPropertyRead systemGetPropertiesGetPropertyWithDefault(String name, String defaultValue) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "(String)System.getProperties().getProperty(\"$name\", \"$defaultValue\")"
            }

            @Override
            String getGroovyExpression() {
                return "System.properties.getProperty(\"$name\", \"$defaultValue\")"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperties().getProperty(\"$name\", \"$defaultValue\")"
            }
        }
    }

    static SystemPropertyRead systemGetPropertiesFilterEntries(String name) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "(String)System.getProperties().entrySet().stream().filter(e -> e.getKey().equals(\"$name\")).findFirst().get().getValue()";
            }

            @Override
            String getGroovyExpression() {
                return "System.properties.entrySet().find { it.key == '$name'}.value"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperties().entries.filter { it.key == \"$name\" }.first().value"
            }
        }
    }

    static SystemPropertyRead integerGetInteger(String name) {
        return new SystemPropertyRead() {
            @Override
            String getKotlinExpression() {
                return "Integer.getInteger(\"$name\")"
            }
        }
    }

    static SystemPropertyRead integerGetIntegerWithPrimitiveDefault(String name, int defaultValue) {
        return new SystemPropertyRead() {
            @Override
            String getGroovyExpression() {
                return "Integer.getInteger(\"$name\", $defaultValue)"
            }

            @Override
            String getKotlinExpression() {
                return "Integer.getInteger(\"$name\", $defaultValue)"
            }
        }
    }

    static SystemPropertyRead integerGetIntegerWithIntegerDefault(String name, int defaultValue) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "Integer.getInteger(\"$name\", Integer.valueOf($defaultValue))"
            }

            @Override
            String getGroovyExpression() {
                return "Integer.getInteger(\"$name\", $defaultValue as Integer)"
            }

            @Override
            String getKotlinExpression() {
                return "Integer.getInteger(\"$name\", $defaultValue)"
            }
        }
    }

    static SystemPropertyRead longGetLong(String name) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "Long.getLong(\"$name\")"
            }

            @Override
            String getGroovyExpression() {
                return "Long.getLong(\"$name\")"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperty(\"$name\")?.toLong()"
            }
        }
    }

    static SystemPropertyRead longGetLongWithPrimitiveDefault(String name, long defaultValue) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "Long.getLong(\"$name\", $defaultValue)"
            }

            @Override
            String getGroovyExpression() {
                return "Long.getLong(\"$name\", $defaultValue as long)"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperty(\"$name\", \"$defaultValue\")?.toLong()"
            }
        }
    }

    static SystemPropertyRead longGetLongWithLongDefault(String name, long defaultValue) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "Long.getLong(\"$name\", Long.valueOf($defaultValue))"
            }

            @Override
            String getGroovyExpression() {
                return "Long.getLong(\"$name\", $defaultValue as Long)"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperty(\"$name\", \"$defaultValue\")?.toLong()"
            }
        }
    }

    static SystemPropertyRead booleanGetBoolean(String name) {
        return new SystemPropertyRead() {
            @Override
            String getJavaExpression() {
                return "Boolean.getBoolean(\"$name\")"
            }

            @Override
            String getGroovyExpression() {
                return "Boolean.getBoolean(\"$name\")"
            }

            @Override
            String getKotlinExpression() {
                return "System.getProperty(\"$name\")?.toBoolean()"
            }
        }
    }
}
