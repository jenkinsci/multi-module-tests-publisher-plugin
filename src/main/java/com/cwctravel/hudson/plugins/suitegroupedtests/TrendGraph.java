/*
 * The MIT License
 *
 * Copyright (c) 2010, Yahoo!, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cwctravel.hudson.plugins.suitegroupedtests;

import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;

import java.awt.Color;
import java.awt.Paint;
import java.io.IOException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitSummaryInfo;

/**
 * User: Benjamin Shine bshine Date: Nov 6, 2009 Time: 5:16:14 PM
 */
public class TrendGraph extends Graph {

	protected final java.util.List<JUnitSummaryInfo> historyList;
	private final String yLabel;
	private final String prefixUrl;
	private final String suffixUrl;
	private boolean failureOnly;

	protected TrendGraph(String prefixUrl, String suffixUrl, String yLabel, java.util.List<JUnitSummaryInfo> historyList) {
		super(-1 /* timestamp */, 500, 200); // TODO: use a good timestamp, so we can take advantage of caching
		this.prefixUrl = prefixUrl;
		this.suffixUrl = suffixUrl;
		this.yLabel = yLabel;
		this.historyList = historyList;
		this.failureOnly = false;
	}

	protected DataSetBuilder<String, ChartLabel> createDataSet() {
		DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<String, ChartLabel>();

		for(JUnitSummaryInfo o: getList()) {
			data.add(o.getFailCount() + o.getErrorCount(), "0Failed", new ChartLabel(o));
			if(!failureOnly) {
				data.add(o.getSkipCount(), "1Skipped", new ChartLabel(o));
				data.add(o.getPassCount(), "2Passed", new ChartLabel(o));

			}
		}
		return data;
	}

	@Override
	public void doPng(StaplerRequest req, StaplerResponse rsp) throws IOException {
		this.failureOnly = Boolean.valueOf(req.getParameter("failureOnly"));
		super.doPng(req, rsp);
	}

	/**
	 * Renders a clickable map.
	 */
	@Override
	public void doMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
		this.failureOnly = Boolean.valueOf(req.getParameter("failureOnly"));
		super.doMap(req, rsp);
	}

	public java.util.List<JUnitSummaryInfo> getList() {
		return historyList;
	}

	@Override
	protected JFreeChart createGraph() {
		final CategoryDataset dataset = createDataSet().build();

		final JFreeChart chart = ChartFactory.createStackedAreaChart(null, // chart
				// title
				null, // unused
				yLabel, // range axis label
				dataset, // data
				PlotOrientation.VERTICAL, // orientation
				false, // include legend
				true, // tooltips
				false // urls
		);

		chart.setBackgroundPaint(Color.white);

		final CategoryPlot plot = chart.getCategoryPlot();

		// plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
		plot.setBackgroundPaint(Color.WHITE);
		plot.setOutlinePaint(null);
		plot.setForegroundAlpha(0.8f);
		// plot.setDomainGridlinesVisible(true);
		// plot.setDomainGridlinePaint(Color.white);
		plot.setRangeGridlinesVisible(true);
		plot.setRangeGridlinePaint(Color.black);

		CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
		plot.setDomainAxis(domainAxis);
		domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
		domainAxis.setLowerMargin(0.0);
		domainAxis.setUpperMargin(0.0);
		domainAxis.setCategoryMargin(0.0);

		final NumberAxis rangeAxis = (NumberAxis)plot.getRangeAxis();
		ChartUtil.adjustChebyshev(dataset, rangeAxis);
		rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
		rangeAxis.setAutoRange(true);

		StackedAreaRenderer ar = new StackedAreaRenderer2() {
			private static final long serialVersionUID = 7962375662395944968L;

			@Override
			public Paint getItemPaint(int row, int column) {
				ChartLabel key = (ChartLabel)dataset.getColumnKey(column);
				if(key.getColor() != null)
					return key.getColor();
				return super.getItemPaint(row, column);
			}

			@Override
			public String generateURL(CategoryDataset dataset, int row, int column) {
				ChartLabel label = (ChartLabel)dataset.getColumnKey(column);
				return prefixUrl + label.getURL() + suffixUrl;
			}

			@Override
			public String generateToolTip(CategoryDataset dataset, int row, int column) {
				ChartLabel label = (ChartLabel)dataset.getColumnKey(column);
				return label.getToolTipText();
			}
		};
		plot.setRenderer(ar);

		ar.setSeriesPaint(0, ColorPalette.RED); // Skips.
		ar.setSeriesPaint(1, ColorPalette.YELLOW); // Failures.
		ar.setSeriesPaint(2, ColorPalette.BLUE); // Total.

		// crop extra space around the graph
		plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

		return chart;
	}
}

class ChartLabel implements Comparable<ChartLabel> {
	JUnitSummaryInfo o;

	public ChartLabel(JUnitSummaryInfo o) {
		this.o = o;
	}

	public String getToolTipText() {
		StringBuilder sb = new StringBuilder();
		sb.append("#" + o.getBuildNumber());
		if(o.getDuration() != 0) {
			sb.append(" ").append(o.getDurationString());
		}
		return sb.toString();
	}

	public String getURL() {
		return Integer.toString(o.getBuildNumber());
	}

	/**
	 * This implementation of compareTo might not be entirely consistent with equals for cases where either object being compared has a null test
	 * object or owner.
	 * 
	 * @see {http://java.sun.com/javase/6/docs/api/java/lang/Comparable.html}
	 * @param that
	 * @return
	 */
	public int compareTo(ChartLabel that) {
		if(that == null)
			throw new NullPointerException();
		if(this.o == that.o)
			return 0;
		if(that.o == null)
			return 1;
		if(this.o == null)
			return -1;
		long buildNumberDiff = this.o.getBuildNumber() - that.o.getBuildNumber();
		return buildNumberDiff < 0 ? -1 : (buildNumberDiff == 0 ? 0 : 1);
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof ChartLabel)) {
			return false;
		}
		ChartLabel that = (ChartLabel)o;
		return this.o == that.o;
	}

	public Color getColor() {
		return null;
	}

	@Override
	public int hashCode() {
		return o.hashCode();
	}

	@Override
	public String toString() {
		if(o == null)
			return "-";
		String l = o.getBuildId();
		return l;
	}

}
