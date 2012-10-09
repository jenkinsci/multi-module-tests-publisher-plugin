package com.cwctravel.hudson.plugins.suitegroupedtests;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.test.TestResultProjectAction;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.cwctravel.hudson.plugins.suitegroupedtests.junit.SuiteGroupResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitSummaryInfo;

public class SuiteGroupResultProjectAction extends TestResultProjectAction {
	private static final Logger LOGGER = Logger.getLogger(SuiteGroupResultProjectAction.class.getName());

	public SuiteGroupResultProjectAction(AbstractProject<?, ?> project) {
		super(project);
	}

	@Override
	public String getUrlName() {
		return "groupedTests";
	}

	public Collection<String> getSuiteNames() {
		SuiteGroupResultAction action = getLastTestResultAction();
		if(action != null) {
			SuiteGroupResult suiteGroupResult = action.getResultAsSuiteGroupResult();
			if(suiteGroupResult != null)
				return suiteGroupResult.getSuiteNames();
		}
		return Collections.EMPTY_LIST;
	}

	@Override
	public SuiteGroupResultAction getLastTestResultAction() {
		final AbstractBuild<?, ?> tb = project.getLastSuccessfulBuild();

		AbstractBuild<?, ?> b = project.getLastBuild();
		while(b != null) {
			SuiteGroupResultAction a = b.getAction(SuiteGroupResultAction.class);
			if(a != null)
				return a;
			if(b == tb)
				// if even the last successful build didn't produce the test result,
				// that means we just don't have any tests configured.
				return null;
			b = b.getPreviousBuild();
		}

		return null;
	}

	public TrendGraph getTrendGraph(String suiteName) {
		SuiteGroupResultAction action = getLastTestResultAction();
		int MAX_HISTORY = 5000; // totally arbitrary, yep
		if(action != null) {
			SuiteGroupResult suiteGroupResult = action.getResultAsSuiteGroupResult();
			if(suiteGroupResult != null) {
				if(suiteGroupResult.getSuiteNames().contains(suiteName)) {
					AbstractProject<?, ?> project = suiteGroupResult.getOwner().getParent();
					JUnitDB junitDB;
					try {
						junitDB = new JUnitDB(project.getRootDir().getAbsolutePath());
						List<JUnitSummaryInfo> historyList = junitDB.summarizeTestSuiteHistory(project.getName(), suiteName, MAX_HISTORY);
						Collections.reverse(historyList);
						return new TrendGraph("/testReport/" + suiteName, "count", historyList);
					}
					catch(SQLException e) {
						LOGGER.warning("Couldn't find the right result group for a trend graph for suite '" + suiteName + "'");
					}
				}
			}
		}
		LOGGER.warning("Couldn't find the right result group for a trend graph for suite '" + suiteName + "'");
		return null;
	}

}