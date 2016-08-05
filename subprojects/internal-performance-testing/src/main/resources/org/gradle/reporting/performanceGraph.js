(function ($) {
  var createPerformanceGraph = function(jsonFile, executionChartId, heapUsageChartId) {
    $(function() {
      $.ajax({ url: jsonFile, dataType: 'json',
        success: function(data) {
          var labels = data.labels;
          var options = {
            series: {
              points: { show: true },
              lines: { show: true }
            },
            legend: { noColumns: 0, margin: 1 },
            grid: { hoverable: true, clickable: true },
            xaxis: { tickFormatter: function(index, value) { return labels[index]; } },
            yaxis: { min: 0 }, selection: { mode: 'xy' } };
          var executionChart = $.plot('#' + executionChartId, data.totalTime, options);
          var heapChart = $.plot('#' + heapUsageChartId, data.heapUsage, options);
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
          $('#' + executionChartId).bind('plothover', function (event, pos, item) {
            if (!item) {
              $('#tooltip').hide();
            } else {
              var text = 'Version: ' + item.series.label + ', date: ' + labels[item.datapoint[0]] + ', execution time: ' + item.datapoint[1] + 's';
              $('#tooltip').html(text).css({top: item.pageY - 10, left: item.pageX + 10}).show();
            }
          }).bind('plotselected', zoomFunction(executionChart)).bind('dblclick', zoomFunction(executionChart, true));
          $('#' + heapUsageChartId).bind('plothover', function (event, pos, item) {
            if (!item) {
              $('#tooltip').hide();
            } else {
              var text = 'Version: ' + item.series.label + ', date: ' + labels[item.datapoint[0]] + ', heap usage: ' + item.datapoint[1] + 'mb';
              $('#tooltip').html(text).css({top: item.pageY - 10, left: item.pageX + 10}).show();
            }
          }).bind('plotselected', zoomFunction(heapChart)).bind('dblclick', zoomFunction(heapChart, true));
        }
      });
    });
  };

  window.performanceTests = {
    createPerformanceGraph: createPerformanceGraph
  }
})($, window);
