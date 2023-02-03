/**
 * This script is used to process a DEBUG log of gradle and
 * generates an output directory with the traces of merged
 * exclude rules and application of exclude rules during a
 * build.
 */
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

File logFile = new File(args[0])
File processedDirectory = findProcessedDirectory()
File excludesDir = new File(processedDirectory, "exclusions")
File allOfDir = new File(processedDirectory, "allOf")
File anyOfDir = new File(processedDirectory, "anyOf")
excludesDir.mkdirs()
allOfDir.mkdirs()
anyOfDir.mkdirs()

def exclusionPattern = ~/DependencyGraphBuilder] ([^ ]+) is excluded from ([^ ]+) by (.+?)\.$/

println("Processing ${logFile} into directory ${processedDirectory}")

int uniqueOpCount = 0
int uniqueExcludeCount = 0

Map<String, Counter> opCounters = [:].withDefault { new Counter(uniqueOpCount++) }
Map<String, Counter> excludeCounters = [:].withDefault { new Counter(uniqueExcludeCount++) }

class Counter {
    final int id
    Counter(int id) { this.id = id}

    int counter = -1

    void inc() {
        counter++
    }

    String getFormattedId() {
        String.format('%06d', id)
    }
}

logFile.eachLine { line ->
    if (line.contains('org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.LoggingExcludeFactory')) {
        line = line.substring(line.indexOf('LoggingExcludeFactory] ') + 23)
        String opName = line.contains('"name": "allOf"') ?  'allOf' : 'anyOf'
        File outDir = opName == 'allOf' ? allOfDir : anyOfDir
        def sorted = sort(line)
        String opNameWithHash = "${opName}-${sorted.hashCode()}"

        Counter counter = opCounters[opNameWithHash]
        counter.inc()

        File mergeOp = new File(outDir, "${counter.formattedId}-${opName}-${counter.counter}.json")
        if (mergeOp.exists()) {
            throw new IllegalStateException("Duplicate file $mergeOp")
        }

        try {
            mergeOp << JsonOutput.prettyPrint(sorted)
        } catch (e) {
            println("Error: $line")
            mergeOp << line
        }
    } else if (line.contains("is excluded from")) {
        def matcher = exclusionPattern.matcher(line)
        matcher.find()
        def (dependency, from, spec) = [matcher.group(1), matcher.group(2), matcher.group(3)]

        def sortedSpec = sort(spec)
        def content = "{\"dependency\": \"$dependency\", \"from\": \"$from\", \"by\": $sortedSpec"
        def id = "$dependency--$from-${content.hashCode()}".replace(':', '_').replace('(', '_').replace(')', '_').replace(':', '_')

        Counter counter = excludeCounters[id]
        counter.inc()

        def excludeFile = new File(excludesDir, "${counter.formattedId}-exclusion-${counter.counter}.txt")
        if (excludeFile.exists()) {
            throw new IllegalStateException("Duplicate file $mergeOp")
        }
        excludeFile << JsonOutput.prettyPrint(content)
    }
}

private sortDeep(entry) {
    if (entry instanceof Map) {
        entry.collectEntries {
            [(it.key), sortDeep(it.value)]
        }.sort()
    } else if (entry instanceof Iterable) {
        entry.collect {
            sortDeep(it)
        }.sort()
    } else {
        entry
    }
}

private sort(String spec) {
    def jsonSlurper = new JsonSlurper()
    def parsed = jsonSlurper.parseText(spec)
    def sorted = sortDeep(parsed)
    JsonOutput.toJson(sorted)
}

private File findProcessedDirectory() {
    int cpt = 0
    File processedDir
    while (processedDir == null) {
        processedDir = new File("processed-${cpt++}")
        if (processedDir.exists()) {
            processedDir = null
        }
    }
    processedDir.mkdirs()
    return processedDir
}
