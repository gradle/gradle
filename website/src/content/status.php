<? if (is_dir("board") ): ?>
  <link href="board/style.css" rel="stylesheet" type="text/css"/>
  <div id="status-board-container">
    <h1>Development Status Board</h1>
    <p>This page gives a very high level view of the current project status.</p>
    <? require "board/board.php"; ?>
  </div>

  <div id="legend-container">
    <h1>Status Legend</h1>
    <? require "board/legend.php"; ?>
  </div>
<? else: ?>
  <p>Status board information is currently unavailable.</p>
<? endif; ?>
