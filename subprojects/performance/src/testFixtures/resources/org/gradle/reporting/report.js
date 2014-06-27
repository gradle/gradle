$(document).ready(function () {

    // Attach controls for column groups in tables
    var controls = $("div#controls");
    var groups = [];
    var slices = [];
    $("tr.control-groups").closest("table").find("tr").each(function() {
        var row = $(this);
        if (row.hasClass('control-groups')) {
            var currentCol = 0;
            slices = [];
            row.find("th").each(function(){
                var e = $(this);
                var title = e.text().trim();
                var startCol = currentCol;
                currentCol += parseInt(e.attr('colspan'));
                var endCol = currentCol;
                if (title.length == 0) {
                    return;
                }
                var id = title.replace(/[^\w]/g, '-').toLowerCase();
                if (groups.indexOf(id) < 0) {
                    groups.push(id);
                    var div = controls.append("<div/>");
                    div.append("<label for='" + id + "'>" + title + "</label>");
                    var checkbox = $("<input>", {type: "checkbox", id: id, checked: true});
                    div.append(checkbox);
                    checkbox.change(function () {
                        if (checkbox.is(':checked')) {
                            $("." + id).show();
                        } else {
                            $("." + id).hide();
                        }
                    });
                }
                e.addClass(id);
                for (var i = startCol; i < endCol; i++) {
                    slices[i] = id;
                }
            });
        } else {
            row.find("td,th").each(function(index){
                $(this).addClass(slices[index]);
            })
        }
    });

    // Add alternate row styles for tables
    $("table").each(function () {
        var counter = 0;
        $(this).find("tr").each(function () {
            var e = $(this);
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
