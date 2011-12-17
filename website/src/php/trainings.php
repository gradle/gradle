<? if (is_dir("trainings")): ?>
  <!--
    <? 
      require_once("trainings/trainings.php");
      $trainings = readTrainings(); 
    ?>
  -->
  <ul>
    <?php foreach ($trainings as $training): ?>
      <li><a href="http://gradleware.com/training"><?= $training->getSummary() ?></li>
    <?php endforeach ?>
  </ul>
<? else: ?>
  <p>Training information temporarily unavailable.</p>
<? endif; ?>
