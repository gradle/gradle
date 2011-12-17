<?
  require_once('php/simplepie.inc');
?>
<div id="status-board-container">
  <p>This page gives a very <em>high level</em> view of the current project roadmap and status and shouldn't be considered exhaustive. For a more fine grained view you can take a look at the <a href="http://issues.gradle.org/browse/GRADLE" title="Gradle">issue tracker</a>.</p>
  <p style="font-weight: bold">You can click on any item to read a corresponding forum post giving more context and rationale.</p>
  <!--
  Beginning parsing of entries...

  <?
    function println($msg) {
      print $msg . PHP_EOL;
    }

    function _valueForLineWithLabel($text, $label) {
      $matches = array();
      if (preg_match('/' . $label . ':\s*(.+)\n/', $text, $matches)) {
        return $matches[1];
      } else {
        return null;
      }
    }
    
    // note: order of this array affects display order;
    $statusCodes = array(
      "CO" => array("name" => "Completed", "description" => "available now or in the trunk waiting for the next release"),
      "IP" => array("name" => "In Progress", "description" => "actively being worked on"),
      "PA" => array("name" => "Paused", "description" => "started, but but not being worked on right now"),
      "PL" => array("name" => "Planned", "description" => "planned out, but implementation hasn't yet begun"),
      "CA" => array("name" => "Cancelled", "description" => "intended at one point, but no longer is")
    );

    $statusNamesToCode = array();
    foreach ($statusCodes as $statusCode => $statusInfo) {
      $statusNamesToCode[strtoupper($statusInfo["name"])] = $statusCode;
    }

    $feed = new SimplePie();
    $feed->set_feed_url("http://forums.gradle.org/gradle/tags/roadmap.rss?sort=recently_created");
    $feed->enable_order_by_date(false);
    $feed->set_cache_location('feed-cache');
    $feed->init();

    $entries = array();
    $entriesByStatus = array();
    foreach ($statusCodes as $statusCode => $statusInfo) {
      $entriesByStatus[$statusCode] = array();
    }

    foreach ($feed->get_items() as $item) {
      println("exracting item: " . $item->get_title());

      $entry = array(
        "title" => $item->get_title(),
        "link" => $item->get_permalink(),
        "summary" => _valueForLineWithLabel($item->get_description(), "summary"),
        "status" => _valueForLineWithLabel($item->get_description(), "status")
      );

      if ($entry["summary"] == null) {
        println(" - no summary, skipping");
        continue;
      }

      if ($entry["status"] == null) {
        println(" - no status line, skipping");
        continue;
      }

      $codeName = _valueForLineWithLabel($item->get_description(), "code");
      if ($codeName == null) {
        println(" - no code line, skipping");
        continue;
      }

      $code = $statusNamesToCode[strtoupper($codeName)];
      if ($code == null) {
        println(" - unknown status name '$codeName', skipping");
        continue;
      }

      // all done, add them to the collections
      array_push($entries, $entry);
      array_push($entriesByStatus[$code], $entry);
    }

  ?>
  -->
  <? if (empty($entriesByStatus)): ?>
    <p class="roadmap-unavailable">The roadmap information is currently unavailable.</p>
  <? else: ?>
    <div id="status-board">
      <? foreach ($entriesByStatus as $statusCode => $entries): ?>
        <? $statusName = $statusCodes[$statusCode]["name"]; $className = strtolower($statusCode); ?>
        <? foreach ($entries as $entry): ?>
          <? $link = $entry["link"]; ?>
          <a href="<?= $link ?>" onclick="this.className += ' clicked';">
            <div class="entry <?= $className ?>">
              <h2><?= $entry["title"] ?></h2>
              <p class="status-name"><?= $statusName ?></p>
              <dl>
                <dt class="info">Summary</dt>
                <dd class="info"><?= $entry["summary"] ?></dd>
                <dt class="status">Status</dt>
                <dd class="status"><?= $entry["status"] ?></dd>
              </dl>
            </div>
          </a>
        <? endforeach; ?>
      <? endforeach; ?>
    </div>
  <? endif ?>
</div>

<div id="legend-container">
  <h1>Status Legend</h1>
  <dl id="status-board-legend">
    <? foreach ($statusCodes as $statusCode => $statusCodeInfo): ?>
      <dt class="<?= strtolower($statusCode) ?>"><?= $statusCodeInfo["name"] ?></dt>
      <dd><?= $statusCodeInfo["description"] ?></dd>
    <? endforeach ?>
  </dl>
</div>
