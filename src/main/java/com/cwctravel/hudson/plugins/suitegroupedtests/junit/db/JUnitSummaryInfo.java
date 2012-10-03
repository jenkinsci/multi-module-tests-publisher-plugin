package com.cwctravel.hudson.plugins.suitegroupedtests.junit.db;

import hudson.Util;

public class JUnitSummaryInfo {
	private String buildId;
	private int buildNumber;

	private String projectName;
	private String suiteName;
	private String packageName;
	private String className;
	private String caseName;

	private long duration;
	private long startTime;

	private long passCount;
	private long failCount;
	private long errorCount;
	private long skipCount;
	private long totalCount;

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public String getBuildId() {
		return buildId;
	}

	public void setBuildId(String buildId) {
		this.buildId = buildId;
	}

	public int getBuildNumber() {
		return buildNumber;
	}

	public void setBuildNumber(int buildNumber) {
		this.buildNumber = buildNumber;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getSuiteName() {
		return suiteName;
	}

	public void setSuiteName(String suiteName) {
		this.suiteName = suiteName;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getCaseName() {
		return caseName;
	}

	public void setCaseName(String caseName) {
		this.caseName = caseName;
	}

	public long getDuration() {
		return duration;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	public long getPassCount() {
		return passCount;
	}

	public void setPassCount(long passCount) {
		this.passCount = passCount;
	}

	public long getFailCount() {
		return failCount;
	}

	public void setFailCount(long failCount) {
		this.failCount = failCount;
	}

	public long getErrorCount() {
		return errorCount;
	}

	public void setErrorCount(long errorCount) {
		this.errorCount = errorCount;
	}

	public long getSkipCount() {
		return skipCount;
	}

	public void setSkipCount(long skipCount) {
		this.skipCount = skipCount;
	}

	public long getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(long totalCount) {
		this.totalCount = totalCount;
	}

	@Override
	public String toString() {
		return "JUnitSummaryInfo [buildId=" + buildId + ", buildNumber=" + buildNumber + ", projectName=" + projectName + ", suiteName=" + suiteName + ", packageName=" + packageName + ", className=" + className + ", duration=" + duration + ", startTime=" + startTime + ", passCount=" + passCount + ", failCount=" + failCount + ", errorCount=" + errorCount + ", skipCount=" + skipCount + ", totalCount=" + totalCount + "]";
	}

	public String getDurationString() {
		return Util.getTimeSpanString((getDuration() * 1000));
	}

}
