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

@Grapes(
    @Grab(group='org.apache.commons', module='commons-csv', version='1.8')
)

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord

def inputFile = new File("labels.csv")
def outputFile = new File("../LABELOWNERS")

def codeOwnersList = []

def platformsMap = [
    'No platform': 'bt-unassigned-maintainers',
    'JVM': 'bt-jvm',
    'Core/execution': 'bt-execution',
    'Core/runtime': 'bt-core-runtime-maintainers',
    'Mixed': 'bt-unassigned-maintainers',
    'GE integration': 'bt-build-scan',
    'Core/configuration': 'bt-configuration-cache',
    'Build infrastructure': 'bt-developer-productivity',
    'No platform': 'bt-unassigned-maintainers',
    '': 'bt-unassigned-maintainers',
    'Software': 'bt-jvm',
    'Extensibility': 'bt-support',
    'IDE': 'bt-ide-experience'
]

// Read CSV file and extract the relevant columns
inputFile.withReader { reader ->
    def csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())
    for (CSVRecord record : csvParser) {
        def labelName = record.get("Label name")
        def platform = record.get("Platform")
        codeOwnersList << String.format("%-45s%3s", labelName, "@gradle/${platformsMap[platform]}")
    }
}

// Sort alphabetically
codeOwnersList.sort()

// Write to the CODEOWNERS file
outputFile.withWriter { writer ->
    writer.write("""## GitHub docs: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners
##
## LABELOWNERS style rules should mirror that of CODEOWNERS:
## 1. Prefer team ownership over individual user ownership.
## 2. GBT-related team should be listed first.
## 3. Try to keep paths alphabetically sorted.
## 4. List individual owners last.
##

# All labels that are not explicitly assigned ('Mixed', 'No platform' or '') are considered
# unassigned and mapped to bt-unassigned-maintainers.

""")

    codeOwnersList.each { line ->
        writer.writeLine(line)
    }
}
