$(function enableAllTooltips() {
    $('[data-toggle="tooltip"]').tooltip();
    $('.data-row').mouseenter(function() {
        $('#section-sign-' + $(this).attr('scenario')).css('opacity', '1');
    }).mouseleave(function() {
        $('#section-sign-' + $(this).attr('scenario')).css('opacity', '0');
    });

    $('.section-sign').click(function() {
        var $temp = $("<input>");
        $("body").append($temp);
        $temp.val(window.location.href.split('#')[0] + $(this).attr('href')).select();
        document.execCommand("copy");
        $temp.remove();
    });
})
