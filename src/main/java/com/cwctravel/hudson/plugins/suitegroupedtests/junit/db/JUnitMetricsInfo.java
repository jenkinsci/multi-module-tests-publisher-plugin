package com.cwctravel.hudson.plugins.suitegroupedtests.junit.db;

public class JUnitMetricsInfo {
	private long totalCount;
	private long successCount;
	private long failCount;
	private long errorCount;
	private long skipCount;

	public long getSuccessCount() {
		return successCount;
	}

	public void setSuccessCount(long successCount) {
		this.successCount = successCount;
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

	public String getSuccessRate() {
		return String.format("%3.2f", (((float)successCount / totalCount) * 100));
	}

	public String getFailureRate() {
		return String.format("%3.2f", (((float)failCount + errorCount) / totalCount) * 100);
	}

	public String getSkipRate() {
		return String.format("%3.2f", ((float)skipCount / totalCount) * 100);
	}
}
