package com.cwctravel.hudson.plugins.suitegroupedtests.junit.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JUnitDB {
	//@formatter:off		
	private static final Object LOCK = new Object();
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_QUERY = 
			"SELECT * FROM JUNIT_TESTS WHERE PROJECT_NAME LIKE ? AND BUILD_ID LIKE ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_QUERY = 
			"SELECT * FROM JUNIT_TESTS WHERE PROJECT_NAME LIKE ? AND BUILD_ID LIKE ? AND SUITE_NAME LIKE ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_PACKAGE_NAME_QUERY = 
			"SELECT * FROM JUNIT_TESTS WHERE PROJECT_NAME LIKE ? AND BUILD_ID LIKE ? AND SUITE_NAME LIKE ? AND PACKAGE_NAME LIKE ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_PACKAGE_NAME_CLASS_NAME_QUERY =
			"SELECT * FROM JUNIT_TESTS WHERE PROJECT_NAME LIKE ? AND BUILD_ID LIKE ? AND SUITE_NAME LIKE ? AND PACKAGE_NAME LIKE ? AND CLASS_NAME LIKE ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_CLASS_NAME_CASE_NAME_QUERY =
			"SELECT * FROM JUNIT_TESTS WHERE PROJECT_NAME LIKE ? AND BUILD_ID LIKE ? AND SUITE_NAME LIKE ? AND PACKAGE_NAME LIKE ? AND CLASS_NAME LIKE ? AND CASE_NAME LIKE ?";

	private static final String JUNIT_TESTS_TEST_CASE_HISTORY_QUERY =
			"SELECT * FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND SUITE_NAME = ? AND PACKAGE_NAME =? AND CLASS_NAME = ? AND CASE_NAME = ? ORDER BY ID DESC";

	private static final String TABLE_NAME_JUNIT_TESTS = "JUNIT_TESTS";

	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CASE_HISTORY_QUERY =			
			"SELECT BUILD_ID, " +
				   "MIN(BUILD_NUMBER) BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "SUITE_NAME, " +
				   "PACKAGE_NAME, " +
				   "CLASS_NAME, " +
				   "CASE_NAME, " +
				   "COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE PROJECT_NAME= ? AND SUITE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? AND CASE_NAME = ? GROUP BY BUILD_ID, PROJECT_NAME, SUITE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME  ORDER BY BUILD_NUMBER DESC";
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CLASS_HISTORY_QUERY =			
			"SELECT BUILD_ID, " +
				   "MIN(BUILD_NUMBER) BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "SUITE_NAME, " +
				   "PACKAGE_NAME, " +
				   "CLASS_NAME, " +
				   "'' CASE_NAME, " +
				   "COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE PROJECT_NAME= ? AND SUITE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? GROUP BY BUILD_ID, PROJECT_NAME, SUITE_NAME, PACKAGE_NAME, CLASS_NAME  ORDER BY BUILD_NUMBER DESC";
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_PACKAGE_HISTORY_QUERY =			
			"SELECT BUILD_ID, " +
				   "MIN(BUILD_NUMBER) BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "SUITE_NAME, " +
				   "PACKAGE_NAME, " +
				   "'' CLASS_NAME, " +
				   "'' CASE_NAME, " +
				   "COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE PROJECT_NAME= ? AND SUITE_NAME = ? AND PACKAGE_NAME = ? GROUP BY BUILD_ID, PROJECT_NAME, SUITE_NAME, PACKAGE_NAME  ORDER BY BUILD_NUMBER DESC";	
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_SUITE_HISTORY_QUERY =			
			"SELECT BUILD_ID, " +
				   "MIN(BUILD_NUMBER) BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "SUITE_NAME, " +
				   "'' PACKAGE_NAME, " +	
				   "'' CLASS_NAME, " +		
				   "'' CASE_NAME, " +
				   "COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE PROJECT_NAME= ? AND SUITE_NAME = ? GROUP BY BUILD_ID, PROJECT_NAME, SUITE_NAME  ORDER BY BUILD_NUMBER DESC";	
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_PROJECT_HISTORY_QUERY =			
			"SELECT BUILD_ID, " +
				   "MIN(BUILD_NUMBER) BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "'' SUITE_NAME, " +
				   "'' PACKAGE_NAME, " +				   
				   "'' CLASS_NAME, " +
				   "'' CASE_NAME, " +
				   "COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE PROJECT_NAME= ? GROUP BY BUILD_ID, PROJECT_NAME ORDER BY BUILD_NUMBER DESC";		
	
	private static final String JUNIT_TESTS_TABLE_CREATE_QUERY = "CREATE TABLE " +
																				"JUNIT_TESTS(ID BIGINT GENERATED ALWAYS AS IDENTITY(START WITH 100, INCREMENT BY 1), " +
																							  "PROJECT_NAME VARCHAR(256) NOT NULL, " +
																							  "BUILD_ID VARCHAR(256) NOT NULL, " +
																							  "BUILD_NUMBER INTEGER NOT NULL, " +
																							  "SUITE_NAME VARCHAR(256) NOT NULL, " +
																							  "PACKAGE_NAME VARCHAR(256) NOT NULL, " +
																							  "CLASS_NAME VARCHAR(512) NOT NULL, " +
																							  "CASE_NAME VARCHAR(256) NOT NULL, " +
																							  "INDEX INT, " +
																							  "STATUS INT, " +
																							  "START_TIME TIMESTAMP, " +
																							  "DURATION BIGINT " +
																							 ")";
	private static final String JUNIT_TESTS_TABLE_INDEX_1 = "CREATE INDEX IDX_JUNIT_TESTS_1 ON JUNIT_TESTS(BUILD_ID, PROJECT_NAME, SUITE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_2 = "CREATE INDEX IDX_JUNIT_TESTS_2 ON JUNIT_TESTS(BUILD_ID, PROJECT_NAME, SUITE_NAME, PACKAGE_NAME, CLASS_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_3 = "CREATE INDEX IDX_JUNIT_TESTS_3 ON JUNIT_TESTS(BUILD_ID, PROJECT_NAME, SUITE_NAME, PACKAGE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_4 = "CREATE INDEX IDX_JUNIT_TESTS_4 ON JUNIT_TESTS(BUILD_ID, PROJECT_NAME, SUITE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_5 = "CREATE INDEX IDX_JUNIT_TESTS_5 ON JUNIT_TESTS(BUILD_ID, PROJECT_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_6 = "CREATE INDEX IDX_JUNIT_TESTS_6 ON JUNIT_TESTS(BUILD_ID)";
	private static final String JUNIT_TESTS_TABLE_INDEX_7 = "CREATE INDEX IDX_JUNIT_TESTS_7 ON JUNIT_TESTS(PROJECT_NAME, SUITE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_8 = "CREATE INDEX IDX_JUNIT_TESTS_8 ON JUNIT_TESTS(PROJECT_NAME, SUITE_NAME, PACKAGE_NAME, CLASS_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_9 = "CREATE INDEX IDX_JUNIT_TESTS_9 ON JUNIT_TESTS(PROJECT_NAME, SUITE_NAME, PACKAGE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_10 = "CREATE INDEX IDX_JUNIT_TESTS_10 ON JUNIT_TESTS(PROJECT_NAME, SUITE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_11 = "CREATE INDEX IDX_JUNIT_TESTS_11 ON JUNIT_TESTS(PROJECT_NAME)";
	
	private static final String JUNIT_TESTS_TABLE_INSERT_QUERY = "INSERT INTO JUNIT_TESTS(PROJECT_NAME, BUILD_ID, BUILD_NUMBER, SUITE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION) " +
																				  "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	
//@formatter:on	
	private static final Logger LOGGER = Logger.getLogger(JUnitDB.class.getName());

	private final String databaseDir;

	private Connection getConnection() throws SQLException {
		String dbUrl = "jdbc:derby:" + databaseDir + "/JUnitDB;create=true;";
		Connection connection = DriverManager.getConnection(dbUrl);
		return connection;
	}

	private void initDB() throws SQLException {
		synchronized(LOCK) {
			Connection connection = getConnection();
			try {
				if(!isTablePresent(connection, TABLE_NAME_JUNIT_TESTS)) {
					createJUnitTestsTable(connection);
					createJUnitTestsTableIndices(connection);
				}
			}
			finally {
				connection.close();
			}
		}
	}

	private void createJUnitTestsTable(Connection connection) throws SQLException {
		Statement s = connection.createStatement();
		try {
			String query = JUNIT_TESTS_TABLE_CREATE_QUERY;
			s.execute(query);
			connection.commit();
		}
		finally {
			connection.rollback();
			s.close();
		}
	}

	private void createJUnitTestsTableIndices(Connection connection) throws SQLException {
		Statement s = connection.createStatement();
		try {
			s.execute(JUNIT_TESTS_TABLE_INDEX_1);
			s.execute(JUNIT_TESTS_TABLE_INDEX_2);
			s.execute(JUNIT_TESTS_TABLE_INDEX_3);
			s.execute(JUNIT_TESTS_TABLE_INDEX_4);
			s.execute(JUNIT_TESTS_TABLE_INDEX_5);
			s.execute(JUNIT_TESTS_TABLE_INDEX_6);
			s.execute(JUNIT_TESTS_TABLE_INDEX_7);
			s.execute(JUNIT_TESTS_TABLE_INDEX_8);
			s.execute(JUNIT_TESTS_TABLE_INDEX_9);
			s.execute(JUNIT_TESTS_TABLE_INDEX_10);
			s.execute(JUNIT_TESTS_TABLE_INDEX_11);
			connection.commit();
		}
		finally {
			connection.rollback();
			s.close();
		}
	}

	private boolean isTablePresent(Connection connection, String tableName) throws SQLException {
		if(tableName != null) {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			ResultSet rS = null;
			try {
				rS = databaseMetaData.getTables(null, null, TABLE_NAME_JUNIT_TESTS, null);
				while(rS.next()) {
					String currentTableName = rS.getString(3);
					if(tableName.equals(currentTableName)) {
						return true;
					}

				}
			}
			finally {
				rS.close();
			}
		}
		return false;
	}

	public JUnitDB(String databaseDir) throws SQLException {
		this.databaseDir = databaseDir;
		initDB();
	}

	public void insertTest(JUnitTestInfo test) throws SQLException {
		List<JUnitTestInfo> tests = new ArrayList<JUnitTestInfo>();
		tests.add(test);

		insertTests(tests);
	}

	public void insertTests(List<JUnitTestInfo> tests) throws SQLException {
		synchronized(LOCK) {
			if(tests != null && !tests.isEmpty()) {
				Connection connection = getConnection();
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_TABLE_INSERT_QUERY);
				try {
					for(JUnitTestInfo junitTestInfo: tests) {
						pS.setString(1, junitTestInfo.getProjectName());
						pS.setString(2, junitTestInfo.getBuildId());
						pS.setInt(3, junitTestInfo.getBuildNumber());
						pS.setString(4, junitTestInfo.getSuiteName());
						pS.setString(5, junitTestInfo.getPackageName());
						pS.setString(6, junitTestInfo.getClassName());
						pS.setString(7, junitTestInfo.getCaseName());
						pS.setInt(8, junitTestInfo.getIndex());
						pS.setInt(9, junitTestInfo.getStatus());
						pS.setTimestamp(10, new Timestamp(junitTestInfo.getStartTime()));
						pS.setLong(11, junitTestInfo.getDuration());
						pS.executeUpdate();
					}
					connection.commit();
				}
				finally {
					connection.rollback();
					connection.close();
				}
			}
		}
	}

	private List<JUnitTestInfo> readTests(ResultSet rS) throws SQLException {
		synchronized(LOCK) {
			List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
			if(rS != null) {
				while(rS.next()) {
					JUnitTestInfo junitTestInfo = new JUnitTestInfo();
					junitTestInfo.setId(rS.getLong(1));
					junitTestInfo.setProjectName(rS.getString(2));
					junitTestInfo.setBuildId(rS.getString(3));
					junitTestInfo.setBuildNumber(rS.getInt(4));
					junitTestInfo.setSuiteName(rS.getString(5));
					junitTestInfo.setPackageName(rS.getString(6));
					junitTestInfo.setClassName(rS.getString(7));
					junitTestInfo.setCaseName(rS.getString(8));
					junitTestInfo.setIndex(rS.getInt(9));
					junitTestInfo.setStatus(rS.getInt(10));
					junitTestInfo.setStartTime(rS.getTimestamp(11).getTime());
					junitTestInfo.setDuration(rS.getLong(12));
					result.add(junitTestInfo);
				}
			}
			return result;
		}
	}

	private List<JUnitSummaryInfo> readSummary(ResultSet rS, int limit) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		if(rS != null) {
			int count = 0;
			while((limit < 0 || count < limit) && rS.next()) {
				JUnitSummaryInfo junitSummaryInfo = new JUnitSummaryInfo();
				junitSummaryInfo.setBuildId(rS.getString(1));
				junitSummaryInfo.setBuildNumber(rS.getInt(2));
				junitSummaryInfo.setProjectName(rS.getString(3));
				junitSummaryInfo.setSuiteName(rS.getString(4));
				junitSummaryInfo.setPackageName(rS.getString(5));
				junitSummaryInfo.setClassName(rS.getString(6));
				junitSummaryInfo.setCaseName(rS.getString(7));
				junitSummaryInfo.setTotalCount(rS.getLong(8));
				junitSummaryInfo.setPassCount(rS.getLong(9));
				junitSummaryInfo.setFailCount(rS.getLong(10));
				junitSummaryInfo.setErrorCount(rS.getLong(11));
				junitSummaryInfo.setSkipCount(rS.getLong(12));
				junitSummaryInfo.setStartTime(rS.getTimestamp(13).getTime());
				junitSummaryInfo.setDuration(rS.getLong(14));
				result.add(junitSummaryInfo);
				count++;
			}
		}
		return result;
	}

	public List<JUnitTestInfo> queryTestsByBuild(String projectName, String buildId) throws SQLException {
		synchronized(LOCK) {
			List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, buildId);

					ResultSet rS = pS.executeQuery();
					try {
						result = readTests(rS);
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitTestInfo> queryTestsBySuite(String projectName, String buildId, String suiteName) throws SQLException {
		synchronized(LOCK) {
			List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, buildId);
					pS.setString(3, suiteName);

					ResultSet rS = pS.executeQuery();
					try {
						result = readTests(rS);
					}
					finally {
						rS.close();
					}

				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitTestInfo> queryTestsByPackage(String projectName, String buildId, String suiteName, String packageName) throws SQLException {
		synchronized(LOCK) {
			List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_PACKAGE_NAME_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, buildId);
					pS.setString(3, suiteName);
					pS.setString(4, packageName);

					ResultSet rS = pS.executeQuery();
					try {
						result = readTests(rS);
					}
					finally {
						rS.close();
					}

				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitTestInfo> queryTestsByClass(String projectName, String buildId, String suiteName, String packageName, String className) throws SQLException {
		synchronized(LOCK) {
			List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_PACKAGE_NAME_CLASS_NAME_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, buildId);
					pS.setString(3, suiteName);
					pS.setString(4, packageName);
					pS.setString(5, className);

					ResultSet rS = pS.executeQuery();
					try {
						result = readTests(rS);
					}
					finally {
						rS.close();
					}

				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public JUnitTestInfo queryTestCase(String projectName, String buildId, String suiteName, String packageName, String className, String caseName) throws SQLException {
		synchronized(LOCK) {
			JUnitTestInfo result = null;
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_ID_SUITE_NAME_CLASS_NAME_CASE_NAME_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, buildId);
					pS.setString(3, suiteName);
					pS.setString(4, packageName);
					pS.setString(5, className);
					pS.setString(6, caseName);

					ResultSet rS = pS.executeQuery();
					try {
						List<JUnitTestInfo> tests = readTests(rS);
						if(!tests.isEmpty()) {
							return tests.get(0);
						}
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitTestInfo> getTestMethodHistory(String projectName, String suiteName, String packageName, String className, String caseName) throws SQLException {
		synchronized(LOCK) {
			List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_TEST_CASE_HISTORY_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, suiteName);
					pS.setString(3, packageName);
					pS.setString(4, className);
					pS.setString(5, caseName);

					ResultSet rS = pS.executeQuery();
					try {
						result = readTests(rS);
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitSummaryInfo> summarizeTestCaseHistory(String projectName, String suiteName, String packageName, String className,
			String caseName, int limit) throws SQLException {
		synchronized(LOCK) {
			List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CASE_HISTORY_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, suiteName);
					pS.setString(3, packageName);
					pS.setString(4, className);
					pS.setString(5, caseName);

					ResultSet rS = pS.executeQuery();
					try {
						result = readSummary(rS, limit);
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitSummaryInfo> summarizeTestClassHistory(String projectName, String suiteName, String packageName, String className, int limit) throws SQLException {
		synchronized(LOCK) {
			List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CLASS_HISTORY_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, suiteName);
					pS.setString(3, packageName);
					pS.setString(4, className);

					ResultSet rS = pS.executeQuery();
					try {
						result = readSummary(rS, limit);
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitSummaryInfo> summarizeTestPackageHistory(String projectName, String suiteName, String packageName, int limit) throws SQLException {
		synchronized(LOCK) {
			List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_PACKAGE_HISTORY_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, suiteName);
					pS.setString(3, packageName);

					ResultSet rS = pS.executeQuery();
					try {
						result = readSummary(rS, limit);
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitSummaryInfo> summarizeTestSuiteHistory(String projectName, String suiteName, int limit) throws SQLException {
		synchronized(LOCK) {
			List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_SUITE_HISTORY_QUERY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, suiteName);

					ResultSet rS = pS.executeQuery();
					try {
						result = readSummary(rS, limit);
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public synchronized List<JUnitSummaryInfo> summarizeTestProjectHistory(String projectName, int limit) throws SQLException {
		synchronized(LOCK) {
			List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_PROJECT_HISTORY_QUERY);
				try {
					pS.setString(1, projectName);

					ResultSet rS = pS.executeQuery();
					try {
						result = readSummary(rS, limit);
					}
					finally {
						rS.close();
					}
				}
				finally {
					pS.close();
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
			return result;
		}
	}

	public List<JUnitTestInfo> filterTestsByDateRange(List<JUnitTestInfo> tests, long startDate, long endDate) {
		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		if(tests != null) {
			for(JUnitTestInfo junitTestInfo: tests) {
				long testStartTime = junitTestInfo.getStartTime();
				if(testStartTime >= startDate && testStartTime <= endDate) {
					result.add(junitTestInfo);
				}
			}
		}
		return result;
	}

	static {
		try {
			Class.forName("org.apache.derby.jdbc.AutoloadedDriver").newInstance();
		}
		catch(InstantiationException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		catch(IllegalAccessException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		catch(ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}

}
