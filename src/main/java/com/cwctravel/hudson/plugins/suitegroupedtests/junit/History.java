/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Tom Huybrechts, Yahoo!, Inc., Seiji Sogabe
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
package com.cwctravel.hudson.plugins.suitegroupedtests.junit;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestObject;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;

import java.awt.Color;
import java.awt.Paint;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import org.kohsuke.stapler.Stapler;

import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitSummaryInfo;

/**
 * History of {@link hudson.tasks.test.TestObject} over time.
 * 
 * @since 1.320
 */
public class History extends hudson.tasks.junit.History {
	private static final Logger LOGGER = Logger.getLogger(History.class.getName());

	private final TestObject testObject;
	private List<JUnitSummaryInfo> historyItems;

	public History(TestObject testObject, int limit) {
		super(null);
		this.testObject = testObject;
		try {
			AbstractProject<?, ?> project = testObject.getOwner().getParent();
			String projectName = project.getName();
			JUnitDB junitDB = new JUnitDB(project.getRootDir().getAbsolutePath());
			if(testObject instanceof SuiteGroupResult) {
				historyItems = junitDB.summarizeTestProjectHistory(projectName, limit);
			}
			else if(testObject instanceof TestResult) {
				TestResult testResult = (TestResult)testObject;
				String suiteName = testResult.getName();
				historyItems = junitDB.summarizeTestSuiteHistory(projectName, suiteName, limit);
			}
			else if(testObject instanceof PackageResult) {
				PackageResult packageResult = (PackageResult)testObject;
				String suiteName = packageResult.getParent().getName();
				String packageName = packageResult.getName();
				historyItems = junitDB.summarizeTestPackageHistory(projectName, suiteName, packageName, limit);
			}
			else if(testObject instanceof ClassResult) {
				ClassResult classResult = (ClassResult)testObject;
				String className = classResult.getName();
				String packageName = classResult.getParent().getName();
				String suiteName = classResult.getParent().getParent().getName();
				historyItems = junitDB.summarizeTestClassHistory(projectName, suiteName, packageName, className, limit);
			}
			else if(testObject instanceof CaseResult) {
				CaseResult caseResult = (CaseResult)testObject;
				String caseName = caseResult.getName();
				String className = caseResult.getParent().getName();
				String packageName = caseResult.getParent().getParent().getName();
				String suiteName = caseResult.getParent().getParent().getParent().getName();
				historyItems = junitDB.summarizeTestCaseHistory(projectName, suiteName, packageName, className, caseName, limit);
			}
		}
		catch(SQLException sE) {
			LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
		}

	}

	@Override
	public TestObject getTestObject() {
		return testObject;
	}

	@Override
	public boolean historyAvailable() {
		return historyItems != null && historyItems.size() > 1;
	}

	/**
	 * Graph of duration of tests over time.
	 */
	@Override
	public Graph getDurationGraph() {
		return new GraphImpl("seconds") {

			@Override
			protected DataSetBuilder<String, ChartLabel> createDataSet() {
				DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<String, ChartLabel>();

				List<JUnitSummaryInfo> list;
				try {
					int startIndex = Integer.parseInt(Stapler.getCurrentRequest().getParameter("start"));
					int endIndex = Integer.parseInt(Stapler.getCurrentRequest().getParameter("end"));
					list = endIndex > historyItems.size() ? historyItems : historyItems.subList(startIndex, endIndex);
				}
				catch(NumberFormatException e) {
					list = historyItems;
				}

				final AbstractProject<?, ?> project = testObject.getOwner().getParent();
				for(JUnitSummaryInfo summaryInfo: list) {
					data.add(((double)summaryInfo.getDuration()) / (1000), "", new ChartLabel(project, testObject.getTestResultAction(), summaryInfo) {
						@Override
						public Color getColor() {
							if(o.getFailCount() + o.getErrorCount() > 0)
								return ColorPalette.RED;
							else if(o.getSkipCount() > 0)
								return ColorPalette.YELLOW;
							else
								return ColorPalette.BLUE;
						}
					});
				}
				return data;
			}

		};
	}

	/**
	 * Graph of # of tests over time.
	 */
	@Override
	public Graph getCountGraph() {
		return new GraphImpl("") {
			@Override
			protected DataSetBuilder<String, ChartLabel> createDataSet() {
				DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<String, ChartLabel>();

				List<JUnitSummaryInfo> list;
				try {
					int startIndex = Integer.parseInt(Stapler.getCurrentRequest().getParameter("start"));
					int endIndex = Integer.parseInt(Stapler.getCurrentRequest().getParameter("end"));
					list = endIndex > historyItems.size() ? historyItems : historyItems.subList(startIndex, endIndex);
				}
				catch(NumberFormatException e) {
					list = historyItems;
				}

				final AbstractProject<?, ?> project = testObject.getOwner().getParent();
				for(JUnitSummaryInfo summaryInfo: list) {
					data.add(summaryInfo.getPassCount(), "2Passed", new ChartLabel(project, testObject.getTestResultAction(), summaryInfo));
					data.add(summaryInfo.getFailCount() + summaryInfo.getErrorCount(), "1Failed", new ChartLabel(project, testObject.getTestResultAction(), summaryInfo));
					data.add(summaryInfo.getSkipCount(), "0Skipped", new ChartLabel(project, testObject.getTestResultAction(), summaryInfo));
				}
				return data;
			}
		};
	}

	private abstract class GraphImpl extends Graph {
		private final String yLabel;

		protected GraphImpl(String yLabel) {
			super(-1, 600, 300); // cannot use timestamp, since ranges may change
			this.yLabel = yLabel;
		}

		protected abstract DataSetBuilder<String, ChartLabel> createDataSet();

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
				private static final long serialVersionUID = -7637931302338728183L;

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
					return label.getUrl();
				}

				@Override
				public String generateToolTip(CategoryDataset dataset, int row, int column) {
					ChartLabel label = (ChartLabel)dataset.getColumnKey(column);
					return label.o.getBuildId() + " : " + label.o.getDurationString();
				}
			};
			plot.setRenderer(ar);
			ar.setSeriesPaint(0, ColorPalette.RED); // Failures.
			ar.setSeriesPaint(1, ColorPalette.YELLOW); // Skips.
			ar.setSeriesPaint(2, ColorPalette.BLUE); // Total.

			// crop extra space around the graph
			plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

			return chart;
		}
	}

	class ChartLabel implements Comparable<ChartLabel> {
		JUnitSummaryInfo o;
		AbstractProject<?, ?> project;
		AbstractTestResultAction<?> testResultAction;
		String url;

		public ChartLabel(AbstractProject<?, ?> project, AbstractTestResultAction<?> testResultAction, JUnitSummaryInfo summaryInfo) {
			this.o = summaryInfo;
			this.project = project;
			this.testResultAction = testResultAction;
			this.url = null;
		}

		public String getUrl() {
			if(this.url == null)
				generateUrl();
			return url;
		}

		private void generateUrl() {
			AbstractBuild<?, ?> build = project.getBuildByNumber(o.getBuildNumber());
			if(build != null) {
				String buildLink = build.getUrl();
				String actionUrl = testResultAction.getUrlName();
				this.url = Hudson.getInstance().getRootUrl() + buildLink + actionUrl + o.getBuildNumber();
			}
		}

		public int compareTo(ChartLabel that) {
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
			String l = Integer.toString(o.getBuildNumber());
			return l;
		}

	}

}
