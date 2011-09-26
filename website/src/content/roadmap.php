<? if (is_dir("board") ): ?>
  <link href="board/style.css" rel="stylesheet" type="text/css"/>
  <div id="status-board-container">
    <h1>Development Roadmap</h1>
    <p>This page gives a very <em>high level</em> view of the current project roadmap and status.</p>
    <p style="font-weight: bold">You can hover over each item for more information, or click the item on a touchscreen device (e.g. iPad).</p>
    <? require "board/board.php"; ?>
  </div>

  <div id="legend-container">
    <h1>Status Legend</h1>
    <? require "board/legend.php"; ?>
  </div>
<? else: ?>
  <p style="text-align: center; color: red">Roadmap information is currently unavailable.</p>
<? endif; ?>
