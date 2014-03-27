$(document).ready(function () {
    $("table").each(function (index, table) {
        var counter = 0;
        $(table).find("tr").each(function (index, row) {
            var e = $(row);
            if (e.children("th").length > 0) {
                counter = 0;
                return;
            }
            if (counter % 2 == 0) {
                e.addClass("table-row-even");
            }
            counter++;
        })
    });
});
