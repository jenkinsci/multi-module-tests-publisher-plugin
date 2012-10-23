package com.cwctravel.hudson.plugins.multimoduletests.junit;

import hudson.Util;

import java.sql.SQLException;

import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitSummaryInfo;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitTestInfo;

public class LazyJUnitSummaryInfo extends JUnitSummaryInfo {
	public static final int SUMMARY_TYPE_CLASS = 0;
	public static final int SUMMARY_TYPE_PACKAGE = 1;
	public static final int SUMMARY_TYPE_MODULE = 2;
	public static final int SUMMARY_TYPE_PROJECT = 3;

	private final int type;

	private final JUnitTestInfo testInfo;

	private JUnitSummaryInfo summary = null;

	private final JUnitDB junitDB;

	public LazyJUnitSummaryInfo(int type, JUnitDB junitDB, JUnitTestInfo testInfo) {
		this.type = type;
		this.testInfo = testInfo;
		this.junitDB = junitDB;
	}

	private void computeSummary() {
		if(summary == null) {
			if(type == SUMMARY_TYPE_CLASS) {
				try {
					summary = junitDB.summarizeTestClassForBuild(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getModuleName(), testInfo.getPackageName(), testInfo.getClassName());
				}
				catch(SQLException sE) {
					throw new JUnitException(sE);
				}
			}
			else if(type == SUMMARY_TYPE_PACKAGE) {
				try {
					summary = junitDB.summarizeTestPackageForBuild(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getModuleName(), testInfo.getPackageName());
				}
				catch(SQLException sE) {
					throw new JUnitException(sE);
				}
			}
			else if(type == SUMMARY_TYPE_MODULE) {
				try {
					summary = junitDB.summarizeTestModuleForBuild(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getModuleName());
				}
				catch(SQLException sE) {
					throw new JUnitException(sE);
				}
			}
			else if(type == SUMMARY_TYPE_PROJECT) {
				try {
					summary = junitDB.summarizeTestProjectForBuild(testInfo.getBuildNumber(), testInfo.getProjectName());
				}
				catch(SQLException sE) {
					throw new JUnitException(sE);
				}
			}
		}
	}

	@Override
	public String toString() {
		if(summary == null) {
			return testInfo.toString();
		}
		else {
			return summary.toString();
		}
	}

	@Override
	public long getStartTime() {
		computeSummary();
		if(summary != null) {
			return summary.getStartTime();
		}
		return super.getStartTime();
	}

	@Override
	public void setStartTime(long startTime) {}

	@Override
	public String getBuildId() {
		return testInfo.getBuildId();
	}

	@Override
	public void setBuildId(String buildId) {}

	@Override
	public int getBuildNumber() {
		return testInfo.getBuildNumber();
	}

	@Override
	public void setBuildNumber(int buildNumber) {}

	@Override
	public String getProjectName() {
		return testInfo.getProjectName();
	}

	@Override
	public void setProjectName(String projectName) {}

	@Override
	public String getModuleName() {
		return testInfo.getModuleName();
	}

	@Override
	public void setModuleName(String moduleName) {}

	@Override
	public String getPackageName() {
		return testInfo.getPackageName();
	}

	@Override
	public void setPackageName(String packageName) {}

	@Override
	public String getClassName() {
		return testInfo.getClassName();
	}

	@Override
	public void setClassName(String className) {}

	@Override
	public String getCaseName() {
		return testInfo.getCaseName();
	}

	@Override
	public void setCaseName(String caseName) {}

	@Override
	public long getDuration() {
		computeSummary();
		if(summary != null) {
			return summary.getDuration();
		}
		return super.getDuration();
	}

	@Override
	public void setDuration(long duration) {}

	@Override
	public long getPassCount() {
		computeSummary();
		if(summary != null) {
			return summary.getPassCount();
		}
		return super.getPassCount();
	}

	@Override
	public void setPassCount(long passCount) {}

	@Override
	public long getFailCount() {
		computeSummary();
		if(summary != null) {
			return summary.getFailCount();
		}
		return super.getFailCount();
	}

	@Override
	public void setFailCount(long failCount) {

	}

	@Override
	public long getErrorCount() {
		computeSummary();
		if(summary != null) {
			return summary.getErrorCount();
		}
		return super.getErrorCount();
	}

	@Override
	public void setErrorCount(long errorCount) {}

	@Override
	public long getSkipCount() {
		computeSummary();
		if(summary != null) {
			return summary.getSkipCount();
		}
		return super.getSkipCount();
	}

	@Override
	public void setSkipCount(long skipCount) {}

	@Override
	public long getTotalCount() {
		computeSummary();
		if(summary != null) {
			return summary.getTotalCount();
		}
		return super.getTotalCount();
	}

	@Override
	public void setTotalCount(long totalCount) {}

	@Override
	public String getDurationString() {
		return Util.getTimeSpanString((getDuration() * 1000));
	}
}
