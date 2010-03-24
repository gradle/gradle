<?php
	/*
	Here, we'll loop through all of the items in the feed, and $item represents the current item in the loop.
	*/
	foreach ($feed->get_items() as $item):
?>
<li>
    <a href="<?php echo $item->get_permalink(); ?>"><?php echo $item->get_title(); ?></a>
</li>
<?php endforeach; ?>