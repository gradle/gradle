import org.akhikhl.gretty.FarmExtension

plugins {
    id("org.gretty") version "4.0.3"
}

// tag::delegateClosureOf[]
farms {
    farm("OldCoreWar", delegateClosureOf<FarmExtension> {
        // Config for the war here
    })
}
// end::delegateClosureOf[]
