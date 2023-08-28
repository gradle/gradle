@Grapes(
    @Grab(group='org.apache.commons', module='commons-csv', version='1.8')
)

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord

def inputFile = new File("labels.csv")
def knownLabels = []
inputFile.withReader { reader ->
    def csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())
    for (CSVRecord record : csvParser) {
        knownLabels << record.get("Label name")
    }
}

// Parse JSON containing new labels
def jsonSlurper = new groovy.json.JsonSlurper()
def newLabels = jsonSlurper.parseText(args[0])

def outputFile = new File("unknown_labels.txt")

// Read previously unknown labels
def existingUnknownLabels = outputFile.readLines()

def unknownLabels = [] as Set
unknownLabels.addAll(newLabels*.name)
unknownLabels.addAll(existingUnknownLabels)
unknownLabels.removeAll(knownLabels)

// Write the results
outputFile.withWriter { writer ->
    writer.with {
        unknownLabels.sort().each {
            writeLine(it)
        }
    }
}
