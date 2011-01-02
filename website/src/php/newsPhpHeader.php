<?php

// Make sure SimplePie is included. You may need to change this to match the location of simplepie.inc.
require_once('../php/simplepie.inc');

// We'll process this feed with all of the default options.
$feed = new SimplePie('http://docs.codehaus.org/createrssfeed.action?types=blogpost&sort=created&showContent=true&showDiff=true&spaces=GRADLE&labelString=&rssType=rss2&maxResults=20&timeSpan=400&publicFeed=true&title=Gradle+RSS+Feed');

// This makes sure that the content is sent to the browser as text/html and the UTF-8 character set (since we didn't change it).
$feed->handle_content_type();

// Let's begin our XHTML webpage code.  The DOCTYPE is supposed to be the very first thing, so we'll keep it on the same line as the closing-PHP tag.
?>