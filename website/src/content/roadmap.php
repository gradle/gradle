<? if (is_dir("board") ): ?>
  <link href="board/style.css" rel="stylesheet" type="text/css"/>
  <div id="status-board-container">
    <h1>Development Roadmap</h1>
    <p>This page gives a very <em>high level</em> view of the current project roadmap and status and shouldn't be considered exhaustive. For a more fine grained view you can take a look at the <a href="http://issues.gradle.org/browse/GRADLE" title="Gradle">issue tracker</a>.</p>
    
    <p style="font-weight: bold">You can click on any item to read a corresponding forum post giving more context and rationale.</p>
    <? require "board/board.php"; ?>
  </div>

  <div id="legend-container">
    <h1>Status Legend</h1>
    <? require "board/legend.php"; ?>
  </div>
<? else: ?>
  <p style="text-align: center; color: red">Roadmap information is currently unavailable.</p>
<? endif; ?>
