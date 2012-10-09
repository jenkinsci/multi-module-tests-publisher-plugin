package com.cwctravel.hudson.plugins.suitegroupedtests.junit.db;

public class JUnitTestInfo {
	public static final int STATUS_SUCCESS = 0;
	public static final int STATUS_FAIL = 1;
	public static final int STATUS_ERROR = 2;
	public static final int STATUS_SKIP = 3;

	private int status;
	private int index;
	private int buildNumber;

	private long id;
	private long startTime;
	private long duration;

	private String projectName;
	private String buildId;

	private String suiteName;
	private String packageName;
	private String className;
	private String caseName;

	private JUnitTestDetailInfo detail;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
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

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getDuration() {
		return duration;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public JUnitTestDetailInfo getDetail() {
		return detail;
	}

	public void setDetail(JUnitTestDetailInfo detail) {
		this.detail = detail;
	}

	@Override
	public String toString() {
		return "JUnitTestInfo [status=" + status + ", index=" + index + ", id=" + id + ", startTime=" + startTime + ", duration=" + duration + ", projectName=" + projectName + ", buildId=" + buildId + ", buildNumber=" + buildNumber + ", suiteName=" + suiteName + ", packageName=" + packageName + ", className=" + className + ", caseName=" + caseName + "]";
	}

}
