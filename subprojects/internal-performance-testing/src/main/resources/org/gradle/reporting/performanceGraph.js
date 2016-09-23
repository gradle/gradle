(function ($) {
  var renderCommitIds = function(commits) {
    return commits.map(function(commit) {
        return commit.substring(0, 7);
    }).join('|');
  }

  var createPerformanceGraph = function(jsonFile, dataSelector, label, unit, chartId) {
    $(function() {
      $.ajax({ url: jsonFile, dataType: 'json',
        success: function(allData) {
          var executionLabels = allData.executionLabels;
          var options = {
            series: {
              points: { show: true },
              lines: { show: true }
            },
            legend: { noColumns: 0, margin: 1 },
            grid: { hoverable: true, clickable: true },
            xaxis: { tickFormatter:
                function(index, value) {
                    if (index === parseInt(index, 10)) { // portable way to check if sth is an integer
                        var executionLabel = executionLabels[index];
                        return executionLabel ? executionLabel.date : "";
                    } else {
                        return "";
                    }
                }
            },
            yaxis: { min: 0 }, selection: { mode: 'xy' } };
          var data = dataSelector(allData)
          var chart = $.plot('#' + chartId, data, options);
          var zoomFunction = function(plot, reset) {
            var reset = reset || false;
            return function (event, ranges) {
              $.each(plot.getXAxes(), function(_, axis) {
                var opts = axis.options;
                opts.min = reset ? null : ranges.xaxis.from;
                opts.max = reset ? null : ranges.xaxis.to;
              });
              $.each(plot.getYAxes(), function(_, axis) {
                var opts = axis.options;
                opts.min = reset ? 0 : ranges.yaxis.from;
                opts.max = reset ? null : ranges.yaxis.to;
              });
              plot.setupGrid();
              plot.draw();
              plot.clearSelection();
            };
          };
          $('#' + chartId).bind('plothover', function (event, pos, item) {
            if (!item) {
              $('#tooltip').hide();
            } else {
              var executionLabel = executionLabels[item.datapoint[0]];
              var revLabel;
              if(item.series.label == executionLabel.branch) {
                revLabel = 'rev: ' + renderCommitIds(executionLabel.commits) + '/' + executionLabel.branch;
              } else {
                revLabel = 'Version: ' + item.series.label;
              }
              var text = revLabel + ', date: ' + executionLabel.date + ', '+ label + ' ' + item.datapoint[1] + unit;
              $('#tooltip').html(text).css({top: item.pageY - 10, left: item.pageX + 10}).show();
            }
          }).bind('plotselected', zoomFunction(chart)).bind('dblclick', zoomFunction(chart, true));
        }
      });
    });
  };

  window.performanceTests = {
    createPerformanceGraph: createPerformanceGraph
  }
})($, window);
