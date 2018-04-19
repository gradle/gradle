def logFile = new File("/tmp/realizations.log")
def log = logFile.text
def eagerSources = log.split("java.lang.Throwable")
def dedupSources = eagerSources.groupBy().sort { a, b -> b.value.size() <=> a.value.size() }.collectEntries { k,v ->
    [ k, v.size() ]
}
def dedup = dedupSources.collect { k, v -> "${v}: ${k}" }.join("\n==========================================\n")
def dedupFile = new File("/tmp/realizations-dedup.log")
dedupFile.text = dedup
println "done"
