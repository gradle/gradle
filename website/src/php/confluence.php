<?php
    $xmlDoc = new DOMDocument();
    $xmlDoc->loadHTMLFile("http://docs.codehaus.org/display/GRADLE/Gradle+0.9+Breaking+Changes");
    $xpath = new DOMXPath($xmlDoc);
    $entries = $xpath->query("//*[@class='wiki-content']");
    foreach ($entries as $entry) {
        $copyDoc = new DOMDocument();
        $copyDoc->appendChild($copyDoc->importNode($entry, true));
        echo $copyDoc->saveHtml();
    }
?>
