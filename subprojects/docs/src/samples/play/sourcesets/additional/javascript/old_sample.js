
var old_cubes = (function() {
    var _i, _len, _results;
    _results = [];
    for (_i = 0, _len = list.length; _i < _len; _i++) {
        num = list[_i];
        _results.push(math.cube(num));
    }
    return _results;
})();
