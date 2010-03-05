<?php
	/*
	Here, we'll loop through all of the items in the feed, and $item represents the current item in the loop.
	*/
	foreach ($feed->get_items() as $item):
?>

<div class="item">
    <h2><?php echo $item->get_title(); ?></h2>

    <p><?php echo $item->get_description(); ?></p>

    <p>
        <small>Posted on <?php echo $item->get_date('j F Y | g:i a'); ?></small>
    </p>
</div>
<?php endforeach; ?>