package com.cwctravel.hudson.plugins.multimoduletests.junit.db;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.cwctravel.hudson.plugins.multimoduletests.junit.io.ReaderWriter;

public class JUnitDB {
	//@formatter:off
	private static final String COMPACT_JUNIT_PACKAGE_SUMMARY_QUERY = "DELETE FROM JUNIT_PACKAGE_SUMMARY WHERE PROJECT_NAME = ? AND BUILD_ID NOT IN (SELECT BUILD_ID FROM JUNIT_ACTIVE_BUILDS WHERE PROJECT_NAME = ?)";
	private static final String COMPACT_JUNIT_MODULE_SUMMARY_QUERY = "DELETE FROM JUNIT_MODULE_SUMMARY WHERE PROJECT_NAME = ? AND BUILD_ID NOT IN (SELECT BUILD_ID FROM JUNIT_ACTIVE_BUILDS WHERE PROJECT_NAME = ?)";
	private static final String COMPACT_JUNIT_PROJECT_SUMMARY_QUERY = "DELETE FROM JUNIT_PROJECT_SUMMARY WHERE PROJECT_NAME = ? AND BUILD_ID NOT IN (SELECT BUILD_ID FROM JUNIT_ACTIVE_BUILDS WHERE PROJECT_NAME = ?)";
	private static final String COMPACT_JUNIT_TESTS_QUERY = "DELETE FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND BUILD_ID NOT IN (SELECT BUILD_ID FROM JUNIT_ACTIVE_BUILDS WHERE PROJECT_NAME = ?)";
	
	
	private static final String CLEAR_JUNIT_ACTIVE_BUILDS_QUERY = "DELETE FROM JUNIT_ACTIVE_BUILDS WHERE PROJECT_NAME = ?";
	
	private static final String JUNIT_TESTS_SELECT_TESTCASE_DETAIL_QUERY = "SELECT ERROR_MESSAGE, ERROR_STACK_TRACE, STDOUT, STDERR FROM JUNIT_TESTS WHERE " +
																			"BUILD_NUMBER = ? AND " +
																			"PROJECT_NAME = ? AND " +
																			"MODULE_NAME = ? AND " +
																			"PACKAGE_NAME = ? AND " +
																			"CLASS_NAME = ? AND " +
																			"CASE_NAME = ?";

	
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_QUERY = 
			"SELECT ID, PROJECT_NAME, BUILD_ID, BUILD_NUMBER, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND BUILD_NUMBER = ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_QUERY = 
			"SELECT ID, PROJECT_NAME, BUILD_ID, BUILD_NUMBER, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND BUILD_NUMBER = ? AND MODULE_NAME = ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_PACKAGE_NAME_QUERY = 
			"SELECT ID, PROJECT_NAME, BUILD_ID, BUILD_NUMBER, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND BUILD_NUMBER = ? AND MODULE_NAME = ? AND PACKAGE_NAME = ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_PACKAGE_NAME_CLASS_NAME_QUERY =
			"SELECT ID, PROJECT_NAME, BUILD_ID, BUILD_NUMBER, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND BUILD_NUMBER = ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ?";
	
	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_CLASS_NAME_CASE_NAME_QUERY =
			"SELECT ID, PROJECT_NAME, BUILD_ID, BUILD_NUMBER, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND BUILD_NUMBER = ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? AND CASE_NAME = ?";

	private static final String JUNIT_TESTS_SELECT_BY_PROJECT_NAME_PRIOR_BUILD_NUMBER_MODULE_NAME_CLASS_NAME_CASE_NAME_QUERY =
			"SELECT ID, PROJECT_NAME, BUILD_ID, BUILD_NUMBER, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION FROM JUNIT_TESTS WHERE BUILD_NUMBER < ? AND PROJECT_NAME LIKE ? AND MODULE_NAME LIKE ? AND PACKAGE_NAME LIKE ? AND CLASS_NAME LIKE ? AND CASE_NAME LIKE ? FETCH FIRST ROW ONLY";

	
	private static final String JUNIT_TESTS_TEST_CASE_HISTORY_QUERY =
			"SELECT ID, PROJECT_NAME, BUILD_ID, BUILD_NUMBER, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME, INDEX, STATUS, START_TIME, DURATION FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND MODULE_NAME = ? AND PACKAGE_NAME =? AND CLASS_NAME = ? AND CASE_NAME = ? ORDER BY ID DESC";

	private static final String TABLE_NAME_JUNIT_TESTS = "JUNIT_TESTS";

	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CASE_HISTORY_QUERY =			
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
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
				   "FROM JUNIT_TESTS WHERE PROJECT_NAME = ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? AND CASE_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME  ORDER BY BUILD_NUMBER DESC";

	private static final String JUNIT_TESTS_FETCH_TEST_CASE_METRICS_FOR_BUILD_QUERY =			
			"SELECT COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER <=? AND PROJECT_NAME = ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? AND CASE_NAME = ?";
	
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CASE_FOR_BUILD_QUERY =			
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
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
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? AND CASE_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME";	
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CASE_FOR_BUILD_PRIOR_TO_QUERY =			
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
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
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER < ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? AND CASE_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME ORDER BY BUILD_NUMBER DESC";	
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CLASS_HISTORY_QUERY =			
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
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
				   "FROM JUNIT_TESTS WHERE PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME  ORDER BY BUILD_NUMBER DESC";
	
	private static final String JUNIT_TESTS_FETCH_TEST_CLASS_METRICS_FOR_BUILD_QUERY =			
			"SELECT COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER <= ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ?";
	

	private static final String JUNIT_TESTS_FETCH_TEST_CLASS_CHILDREN_FOR_BUILD_QUERY =			
			"SELECT ID, " +
					"PROJECT_NAME, " +
					"BUILD_ID, " +
					"BUILD_NUMBER, " +
					"MODULE_NAME, " +
					"PACKAGE_NAME, " +
					"CLASS_NAME, " +
					"CASE_NAME, " +
					"INDEX, " +
					"STATUS, " +
					"START_TIME,  " +
					"DURATION " +
					"FROM JUNIT_TESTS " +
					"WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ?";
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CLASS_FOR_BUILD_QUERY =			
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
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
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME ";

	private static final String JUNIT_TESTS_SUMMARIZE_TEST_CLASS_FOR_BUILD_PRIOR_TO_QUERY =			
			"SELECT MIN(BUILD_ID), " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
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
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER < ? AND PROJECT_NAME = ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? AND CLASS_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME ORDER BY BUILD_NUMBER DESC FETCH FIRST ROW ONLY";	
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_PACKAGE_HISTORY_QUERY =			
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "PACKAGE_NAME, " +
				   "'' CLASS_NAME, " +
				   "'' CASE_NAME, " +
				   "SUM(TOTAL_COUNT) TOTAL_COUNT, " +
				   "SUM(PASS_COUNT) PASS_COUNT, " +
				   "SUM(FAIL_COUNT) FAIL_COUNT, " +
				   "SUM(ERROR_COUNT) ERROR_COUNT, " +
				   "SUM(SKIP_COUNT) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_PACKAGE_SUMMARY WHERE PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME ORDER BY BUILD_NUMBER DESC";

	private static final String JUNIT_TESTS_FETCH_TEST_PACKAGE_CHILDREN_FOR_BUILD_QUERY =			
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
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
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME";
	

	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_PACKAGES_FOR_BUILD_QUERY =			
			"INSERT INTO JUNIT_PACKAGE_SUMMARY(BUILD_ID, BUILD_NUMBER,PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, TOTAL_COUNT, PASS_COUNT, FAIL_COUNT, ERROR_COUNT, SKIP_COUNT, START_TIME, DURATION) " +
				   "SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "PACKAGE_NAME, " +
				   "COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME";
	
	private static final String JUNIT_TESTS_FETCH_TEST_PACKAGE_METRICS_FOR_BUILD_QUERY =			
			"SELECT SUM(TOTAL_COUNT) TOTAL_COUNT, " +
				   "SUM(PASS_COUNT) PASS_COUNT, " +
				   "SUM(FAIL_COUNT) FAIL_COUNT, " +
				   "SUM(ERROR_COUNT) ERROR_COUNT, " +
				   "SUM(SKIP_COUNT) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_PACKAGE_SUMMARY WHERE BUILD_NUMBER <= ? AND PROJECT_NAME = ? AND MODULE_NAME = ? AND PACKAGE_NAME = ?";	

	private static final String JUNIT_TESTS_FETCH_TEST_PACKAGE_SUMMARY_FOR_BUILD_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "PACKAGE_NAME, " +
				   "'' CLASS_NAME, " +
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_PACKAGE_SUMMARY WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ?";
	
	private static final String JUNIT_TESTS_FETCH_TEST_PACKAGE_SUMMARY_FOR_BUILD_PRIOR_TO_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "PACKAGE_NAME, " +
				   "'' CLASS_NAME, " +
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_PACKAGE_SUMMARY WHERE BUILD_NUMBER < ? AND PROJECT_NAME= ? AND MODULE_NAME = ? AND PACKAGE_NAME = ? ORDER BY BUILD_NUMBER DESC FETCH FIRST ROW ONLY";		
	
	private static final String JUNIT_FETCH_TEST_MODULE_SUMMARY_HISTORY_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "'' PACKAGE_NAME, " +	
				   "'' CLASS_NAME, " +		
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_MODULE_SUMMARY WHERE PROJECT_NAME= ? AND MODULE_NAME = ? ORDER BY BUILD_NUMBER DESC";	

	private static final String JUNIT_TESTS_FETCH_TEST_MODULE_CHILDREN_FOR_BUILD_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "PACKAGE_NAME, " +	
				   "'' CLASS_NAME, " +		
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_PACKAGE_SUMMARY WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? ";
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_MODULE_FOR_BUILD_QUERY =			
			"INSERT INTO JUNIT_MODULE_SUMMARY(BUILD_ID, BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, TOTAL_COUNT, PASS_COUNT, FAIL_COUNT, ERROR_COUNT, SKIP_COUNT, START_TIME, DURATION) " + 
				   "SELECT MIN(BUILD_ID) BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "COUNT(*) TOTAL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
				   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
				   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
				   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_TESTS WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ? GROUP BY BUILD_NUMBER, PROJECT_NAME, MODULE_NAME";
	
	private static final String JUNIT_TESTS_FETCH_TEST_MODULE_METRICS_FOR_BUILD_QUERY =			
			"SELECT SUM(TOTAL_COUNT) TOTAL_COUNT, " +
				   "SUM(PASS_COUNT) PASS_COUNT, " +
				   "SUM(FAIL_COUNT) FAIL_COUNT, " +
				   "SUM(ERROR_COUNT) ERROR_COUNT, " +
				   "SUM(SKIP_COUNT) SKIP_COUNT, " +
				   "MIN(START_TIME) START_TIME, " +
				   "SUM(DURATION) DURATION " +
				   "FROM JUNIT_MODULE_SUMMARY WHERE BUILD_NUMBER <= ? AND PROJECT_NAME= ? AND MODULE_NAME = ?";	

	private static final String JUNIT_FETCH_TEST_MODULE_SUMMARY_FOR_BUILD_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "'' PACKAGE_NAME, " +	
				   "'' CLASS_NAME, " +		
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_MODULE_SUMMARY WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? AND MODULE_NAME = ?";
	
	private static final String JUNIT_FETCH_TEST_MODULE_SUMMARY_FOR_BUILD_PRIOR_TO_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "'' PACKAGE_NAME, " +	
				   "'' CLASS_NAME, " +		
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_MODULE_SUMMARY WHERE BUILD_NUMBER < ? AND PROJECT_NAME= ? AND MODULE_NAME = ? ORDER BY BUILD_NUMBER DESC FETCH FIRST ROW ONLY";	
	
	private static final String JUNIT_TESTS_FETCH_TEST_MODULE_SUMMARY_FOR_BUILD_NO_LATER_THAN_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "MODULE_NAME, " +
				   "'' PACKAGE_NAME, " +	
				   "'' CLASS_NAME, " +		
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_MODULE_SUMMARY WHERE BUILD_NUMBER <= ? AND PROJECT_NAME= ? AND MODULE_NAME = ? ORDER BY BUILD_NUMBER DESC FETCH FIRST ROW ONLY";	
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_PROJECT_HISTORY_QUERY =			
			"SELECT BUILD_ID, " +
				   "BUILD_NUMBER, " +
				   "PROJECT_NAME, " +
				   "'' MODULE_NAME, " +
				   "'' PACKAGE_NAME, " +				   
				   "'' CLASS_NAME, " +
				   "'' CASE_NAME, " +
				   "TOTAL_COUNT, " +
				   "PASS_COUNT, " +
				   "FAIL_COUNT, " +
				   "ERROR_COUNT, " +
				   "SKIP_COUNT, " +
				   "START_TIME, " +
				   "DURATION " +
				   "FROM JUNIT_PROJECT_SUMMARY WHERE PROJECT_NAME= ? ORDER BY BUILD_NUMBER DESC";	

	private static final String JUNIT_TESTS_FETCH_TEST_PROJECT_CHILDREN_FOR_BUILD_QUERY = 	
			"SELECT MIN(BUILD_ID) BUILD_ID, " +
			   "BUILD_NUMBER, " +
			   "PROJECT_NAME, " +
			   "MODULE_NAME, " +
			   "'' PACKAGE_NAME, " +	
			   "'' CLASS_NAME, " +		
			   "'' CASE_NAME, " +
			   "TOTAL_COUNT, " +
			   "PASS_COUNT, " +
			   "FAIL_COUNT, " +
			   "ERROR_COUNT, " +
			   "SKIP_COUNT, " +
			   "START_TIME, " +
			   "DURATION " +
			   "FROM JUNIT_MODULE_SUMMARY WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ?";
	
	private static final String JUNIT_TESTS_SUMMARIZE_TEST_PROJECT_FOR_BUILD_QUERY = 	
			"INSERT INTO JUNIT_PROJECT_SUMMARY(BUILD_ID, BUILD_NUMBER, PROJECT_NAME, TOTAL_COUNT, PASS_COUNT, FAIL_COUNT, ERROR_COUNT, SKIP_COUNT, START_TIME, DURATION) " +
			   "SELECT MIN(BUILD_ID) BUILD_ID, " +
			   "BUILD_NUMBER, " +
			   "PROJECT_NAME, " +
			   "COUNT(*) TOTAL_COUNT, " +
			   "SUM((CASE WHEN STATUS = 0 THEN 1 ELSE 0 END)) PASS_COUNT, " +
			   "SUM((CASE WHEN STATUS = 1 THEN 1 ELSE 0 END)) FAIL_COUNT, " +
			   "SUM((CASE WHEN STATUS = 2 THEN 1 ELSE 0 END)) ERROR_COUNT, " +
			   "SUM((CASE WHEN STATUS = 3 THEN 1 ELSE 0 END)) SKIP_COUNT, " +
			   "MIN(START_TIME) START_TIME, " +
			   "SUM(DURATION) DURATION " +
			   "FROM JUNIT_TESTS WHERE BUILD_NUMBER = ? AND PROJECT_NAME= ? GROUP BY BUILD_NUMBER, PROJECT_NAME";
	
	private static final String JUNIT_TESTS_FETCH_TEST_PROJECT_METRICS_FOR_BUILD_QUERY = 	
			"SELECT TOTAL_COUNT, " +
			   "PASS_COUNT, " +
			   "FAIL_COUNT, " +
			   "ERROR_COUNT, " +
			   "SKIP_COUNT, " +
			   "START_TIME, " +
			   "DURATION " +
			   "FROM JUNIT_PROJECT_SUMMARY WHERE BUILD_NUMBER <= ? AND PROJECT_NAME = ?";	
	
	private static final String JUNIT_TESTS_FETCH_TEST_PROJECT_SUMMARY_FOR_BUILD_QUERY = 	
			"SELECT BUILD_ID, " +
			   "BUILD_NUMBER, " +
			   "PROJECT_NAME, " +
			   "'' MODULE_NAME, " +
			   "'' PACKAGE_NAME, " +	
			   "'' CLASS_NAME, " +		
			   "'' CASE_NAME, " +
			   "TOTAL_COUNT, " +
			   "PASS_COUNT, " +
			   "FAIL_COUNT, " +
			   "ERROR_COUNT, " +
			   "SKIP_COUNT, " +
			   "START_TIME, " +
			   "DURATION " +
			   "FROM JUNIT_PROJECT_SUMMARY WHERE BUILD_NUMBER = ? AND PROJECT_NAME = ?";	
	
	private static final String JUNIT_TESTS_FETCH_TEST_PROJECT_SUMMARY_FOR_BUILD_PRIOR_TO_QUERY = 	
			"SELECT BUILD_ID, " +
			   "BUILD_NUMBER, " +
			   "PROJECT_NAME, " +
			   "'' MODULE_NAME, " +
			   "'' PACKAGE_NAME, " +	
			   "'' CLASS_NAME, " +		
			   "'' CASE_NAME, " +
			   "TOTAL_COUNT, " +
			   "PASS_COUNT, " +
			   "FAIL_COUNT, " +
			   "ERROR_COUNT, " +
			   "SKIP_COUNT, " +
			   "START_TIME, " +
			   "DURATION " +
			   "FROM JUNIT_PROJECT_SUMMARY WHERE BUILD_NUMBER < ? AND PROJECT_NAME = ? ORDER BY BUILD_NUMBER DESC FETCH FIRST ROW ONLY";	
	
	private static final String JUNIT_TESTS_FETCH_TEST_PROJECT_SUMMARY_FOR_BUILD_NO_LATER_THAN_QUERY = 	
			"SELECT BUILD_ID, " +
			   "BUILD_NUMBER, " +
			   "PROJECT_NAME, " +
			   "'' MODULE_NAME, " +
			   "'' PACKAGE_NAME, " +	
			   "'' CLASS_NAME, " +		
			   "'' CASE_NAME, " +
			   "TOTAL_COUNT, " +
			   "PASS_COUNT, " +
			   "FAIL_COUNT, " +
			   "ERROR_COUNT, " +
			   "SKIP_COUNT, " +
			   "START_TIME, " +
			   "DURATION " +
			   "FROM JUNIT_PROJECT_SUMMARY WHERE BUILD_NUMBER <= ? AND PROJECT_NAME = ? ORDER BY BUILD_NUMBER DESC FETCH FIRST ROW ONLY";	
	
	private static final String JUNIT_TESTS_FETCH_PROPERTY = "SELECT VALUE FROM JUNIT_PROPERTIES WHERE PROJECT_NAME = ? AND NAME = UPPER(?)";
	private static final String JUNIT_TESTS_INSERT_PROPERTY = "INSERT INTO JUNIT_PROPERTIES(PROJECT_NAME, NAME, VALUE) VALUES(?, UPPER(?), ?)";
	private static final String JUNIT_TESTS_UPDATE_PROPERTY = "UPDATE JUNIT_PROPERTIES SET VALUE = ? WHERE PROJECT_NAME = ? AND NAME = UPPER(?)";
	
	private static final String JUNIT_TESTS_TABLE_CREATE_QUERY = "CREATE TABLE " +
																				"JUNIT_TESTS(ID BIGINT GENERATED ALWAYS AS IDENTITY(START WITH 100, INCREMENT BY 1), " +
																							  "PROJECT_NAME VARCHAR(256) NOT NULL, " +
																							  "BUILD_ID VARCHAR(256) NOT NULL, " +
																							  "BUILD_NUMBER INTEGER NOT NULL, " +
																							  "MODULE_NAME VARCHAR(256) NOT NULL, " +
																							  "PACKAGE_NAME VARCHAR(256) NOT NULL, " +
																							  "CLASS_NAME VARCHAR(512) NOT NULL, " +
																							  "CASE_NAME VARCHAR(256) NOT NULL, " +
																							  "INDEX INT, " +
																							  "STATUS INT, " +
																							  "START_TIME TIMESTAMP, " +
																							  "DURATION BIGINT, " +
																							  "ERROR_MESSAGE VARCHAR(1024), " +
																							  "ERROR_STACK_TRACE VARCHAR(8192), " +
																							  "STDOUT CLOB(64 M), " + 
																							  "STDERR CLOB(64 M) " + 
																							 ")";
	
	private static final String JUNIT_PROJECT_SUMMARY_TABLE_CREATE_QUERY = "CREATE TABLE " +
			"JUNIT_PROJECT_SUMMARY(ID BIGINT GENERATED ALWAYS AS IDENTITY(START WITH 100, INCREMENT BY 1), " +
						  "PROJECT_NAME VARCHAR(256) NOT NULL, " +
						  "BUILD_ID VARCHAR(256) NOT NULL, " +
						  "BUILD_NUMBER INTEGER NOT NULL, " +						  
						  "TOTAL_COUNT BIGINT NOT NULL, " +
						  "PASS_COUNT BIGINT NOT NULL, " +
						  "FAIL_COUNT BIGINT NOT NULL, " +
						  "ERROR_COUNT BIGINT NOT NULL, " +
						  "SKIP_COUNT BIGINT NOT NULL, " +
						  "START_TIME TIMESTAMP, " +
						  "DURATION BIGINT " +
						 ")";		
	
	private static final String JUNIT_MODULE_SUMMARY_TABLE_CREATE_QUERY = "CREATE TABLE " +
			"JUNIT_MODULE_SUMMARY(ID BIGINT GENERATED ALWAYS AS IDENTITY(START WITH 100, INCREMENT BY 1), " +
						  "PROJECT_NAME VARCHAR(256) NOT NULL, " +
						  "BUILD_ID VARCHAR(256) NOT NULL, " +
						  "BUILD_NUMBER INTEGER NOT NULL, " +
						  "MODULE_NAME VARCHAR(256) NOT NULL, " +
						  "TOTAL_COUNT BIGINT NOT NULL, " +
						  "PASS_COUNT BIGINT NOT NULL, " +
						  "FAIL_COUNT BIGINT NOT NULL, " +
						  "ERROR_COUNT BIGINT NOT NULL, " +
						  "SKIP_COUNT BIGINT NOT NULL, " +
						  "START_TIME TIMESTAMP, " +
						  "DURATION BIGINT " +
						 ")";	
	
	private static final String JUNIT_PACKAGE_SUMMARY_TABLE_CREATE_QUERY = "CREATE TABLE " +
			"JUNIT_PACKAGE_SUMMARY(ID BIGINT GENERATED ALWAYS AS IDENTITY(START WITH 100, INCREMENT BY 1), " +
						  "PROJECT_NAME VARCHAR(256) NOT NULL, " +
						  "BUILD_ID VARCHAR(256) NOT NULL, " +
						  "BUILD_NUMBER INTEGER NOT NULL, " +
						  "MODULE_NAME VARCHAR(256) NOT NULL, " +
						  "PACKAGE_NAME VARCHAR(256) NOT NULL, " +
						  "TOTAL_COUNT BIGINT NOT NULL, " +
						  "PASS_COUNT BIGINT NOT NULL, " +
						  "FAIL_COUNT BIGINT NOT NULL, " +
						  "ERROR_COUNT BIGINT NOT NULL, " +
						  "SKIP_COUNT BIGINT NOT NULL, " +
						  "START_TIME TIMESTAMP, " +
						  "DURATION BIGINT " +
						 ")";		
	
	private static final String JUNIT_PROPERTIES_TABLE_CREATE_QUERY = "CREATE TABLE " +
																"JUNIT_PROPERTIES(ID BIGINT GENERATED ALWAYS AS IDENTITY(START WITH 100, INCREMENT BY 1), " +
																				 "PROJECT_NAME VARCHAR(256) NOT NULL, " +
																				 "NAME VARCHAR(512) NOT NULL, " +
																				 "VALUE VARCHAR(1024) NOT NULL)";
	
	private static final String JUNIT_ACTIVE_BUILDS_TABLE_CREATE_QUERY = "CREATE TABLE " +
			"JUNIT_ACTIVE_BUILDS(ID BIGINT GENERATED ALWAYS AS IDENTITY(START WITH 100, INCREMENT BY 1), " +
							 "PROJECT_NAME VARCHAR(256) NOT NULL, " +
							 "BUILD_ID VARCHAR(256) NOT NULL)";	
	
	private static final String JUNIT_TESTS_TABLE_INDEX_1 = "CREATE INDEX IDX_JUNIT_TESTS_1 ON JUNIT_TESTS(BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_2 = "CREATE INDEX IDX_JUNIT_TESTS_2 ON JUNIT_TESTS(BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_3 = "CREATE INDEX IDX_JUNIT_TESTS_3 ON JUNIT_TESTS(BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_4 = "CREATE INDEX IDX_JUNIT_TESTS_4 ON JUNIT_TESTS(BUILD_NUMBER, PROJECT_NAME, MODULE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_5 = "CREATE INDEX IDX_JUNIT_TESTS_5 ON JUNIT_TESTS(BUILD_NUMBER, PROJECT_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_6 = "CREATE INDEX IDX_JUNIT_TESTS_6 ON JUNIT_TESTS(BUILD_NUMBER)";
	private static final String JUNIT_TESTS_TABLE_INDEX_7 = "CREATE INDEX IDX_JUNIT_TESTS_7 ON JUNIT_TESTS(PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME, CASE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_8 = "CREATE INDEX IDX_JUNIT_TESTS_8 ON JUNIT_TESTS(PROJECT_NAME, MODULE_NAME, PACKAGE_NAME, CLASS_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_9 = "CREATE INDEX IDX_JUNIT_TESTS_9 ON JUNIT_TESTS(PROJECT_NAME, MODULE_NAME, PACKAGE_NAME)";
	private static final String JUNIT_TESTS_TABLE_INDEX_10 = "CREATE INDEX IDX_JUNIT_TESTS_10 ON JUNIT_TESTS(PROJECT_NAME, MODULE_NAME)";	
	private static final String JUNIT_TESTS_TABLE_INDEX_11 = "CREATE INDEX IDX_JUNIT_TESTS_11 ON JUNIT_TESTS(PROJECT_NAME, BUILD_NUMBER)";
	private static final String JUNIT_TESTS_TABLE_INDEX_12 = "CREATE INDEX IDX_JUNIT_TESTS_12 ON JUNIT_TESTS(PROJECT_NAME)";
	
	private static final String JUNIT_INDEX_UNIQUE_PROJECT_SUMMARY_TABLE_1= "CREATE UNIQUE INDEX IDXUQ_JUNIT_PROJECT_SUMMARY_1 ON JUNIT_PROJECT_SUMMARY(BUILD_NUMBER, PROJECT_NAME)";
	private static final String JUNIT_INDEX_PROJECT_SUMMARY_TABLE_1= "CREATE INDEX IDX_JUNIT_PROJECT_SUMMARY_1 ON JUNIT_PROJECT_SUMMARY(PROJECT_NAME)";
	
	private static final String JUNIT_INDEX_UNIQUE_MODULE_SUMMARY_TABLE_1= "CREATE UNIQUE INDEX IDXUQ_JUNIT_MODULE_SUMMARY_1 ON JUNIT_MODULE_SUMMARY(BUILD_NUMBER, PROJECT_NAME, MODULE_NAME)";
	private static final String JUNIT_INDEX_MODULE_SUMMARY_TABLE_1= "CREATE INDEX IDX_JUNIT_MODULE_SUMMARY_1 ON JUNIT_MODULE_SUMMARY(PROJECT_NAME, MODULE_NAME)";
	
	private static final String JUNIT_INDEX_UNIQUE_PACKAGE_SUMMARY_TABLE_1= "CREATE UNIQUE INDEX IDXUQ_JUNIT_PACKAGE_SUMMARY_1 ON JUNIT_PACKAGE_SUMMARY(BUILD_NUMBER, PROJECT_NAME, MODULE_NAME, PACKAGE_NAME)";
	private static final String JUNIT_INDEX_PACKAGE_SUMMARY_TABLE_1= "CREATE INDEX IDX_JUNIT_PACKAGE_SUMMARY_1 ON JUNIT_PACKAGE_SUMMARY(PROJECT_NAME, MODULE_NAME, PACKAGE_NAME)";
	
	private static final String JUNIT_INDEX_UNIQUE_PROPERTIES_TABLE_1= "CREATE UNIQUE INDEX IDXUQ_JUNIT_PROPERTIES_1 ON JUNIT_PROPERTIES(PROJECT_NAME, NAME)";
	
	private static final String JUNIT_TESTS_TABLE_INSERT_QUERY = "INSERT INTO JUNIT_TESTS(PROJECT_NAME, " +
																						 "BUILD_ID, " +
																						 "BUILD_NUMBER, " +
																						 "MODULE_NAME, " +
																						 "PACKAGE_NAME, " +
																						 "CLASS_NAME, " +
																						 "CASE_NAME, " +
																						 "INDEX, " +
																						 "STATUS, " +
																						 "START_TIME, " +
																						 "DURATION, " +
																						 "ERROR_MESSAGE, " +
																						 "ERROR_STACK_TRACE, " +
																						 "STDOUT, " +
																						 "STDERR) " +
																				  "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	
	
private static final String JUNIT_ACTIVE_BUILDS_TABLE_INSERT_QUERY = "INSERT INTO JUNIT_ACTIVE_BUILDS(PROJECT_NAME, " +
																						 "BUILD_ID) " +
																				 "VALUES(?, ?)";
//@formatter:on	
	private static final Logger LOGGER = Logger.getLogger(JUnitDB.class.getName());

	private final String databaseDir;

	private String getDatabasePath() {
		return databaseDir + "/JUnitDB";
	}

	private Connection getConnection() throws SQLException {
		String dbUrl = "jdbc:derby:" + getDatabasePath() + ";create=true;";
		Connection connection = DriverManager.getConnection(dbUrl);
		connection.setAutoCommit(false);
		return connection;
	}

	private void initDB() throws SQLException {
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

	private void createJUnitTestsTable(Connection connection) throws SQLException {
		Statement s = connection.createStatement();
		try {
			String query = JUNIT_TESTS_TABLE_CREATE_QUERY;
			s.execute(query);

			query = JUNIT_PROJECT_SUMMARY_TABLE_CREATE_QUERY;
			s.execute(query);

			query = JUNIT_MODULE_SUMMARY_TABLE_CREATE_QUERY;
			s.execute(query);

			query = JUNIT_PACKAGE_SUMMARY_TABLE_CREATE_QUERY;
			s.execute(query);

			query = JUNIT_PROPERTIES_TABLE_CREATE_QUERY;
			s.execute(query);

			query = JUNIT_ACTIVE_BUILDS_TABLE_CREATE_QUERY;
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
			s.execute(JUNIT_TESTS_TABLE_INDEX_12);

			s.execute(JUNIT_INDEX_UNIQUE_PROJECT_SUMMARY_TABLE_1);
			s.execute(JUNIT_INDEX_PROJECT_SUMMARY_TABLE_1);

			s.execute(JUNIT_INDEX_UNIQUE_MODULE_SUMMARY_TABLE_1);
			s.execute(JUNIT_INDEX_MODULE_SUMMARY_TABLE_1);

			s.execute(JUNIT_INDEX_UNIQUE_PACKAGE_SUMMARY_TABLE_1);
			s.execute(JUNIT_INDEX_PACKAGE_SUMMARY_TABLE_1);

			s.execute(JUNIT_INDEX_UNIQUE_PROPERTIES_TABLE_1);

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
				connection.rollback();
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
		if(tests != null && !tests.isEmpty()) {
			Connection connection = getConnection();
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_TABLE_INSERT_QUERY);
			try {
				for(JUnitTestInfo junitTestInfo: tests) {
					pS.setString(1, junitTestInfo.getProjectName());
					pS.setString(2, junitTestInfo.getBuildId());
					pS.setInt(3, junitTestInfo.getBuildNumber());
					pS.setString(4, junitTestInfo.getModuleName());
					pS.setString(5, junitTestInfo.getPackageName());
					pS.setString(6, junitTestInfo.getClassName());
					pS.setString(7, junitTestInfo.getCaseName());
					pS.setInt(8, junitTestInfo.getIndex());
					pS.setInt(9, junitTestInfo.getStatus());
					pS.setTimestamp(10, new Timestamp(junitTestInfo.getStartTime()));
					pS.setLong(11, junitTestInfo.getDuration());

					JUnitTestDetailInfo junitTestDetailInfo = junitTestInfo.getDetail();
					if(junitTestDetailInfo != null) {
						pS.setString(12, truncate(junitTestDetailInfo.getErrorMessage(), 1024));
						pS.setString(13, truncate(junitTestDetailInfo.getErrorStackTrace(), 8192));

						Reader stdoutReader = junitTestDetailInfo.getStdout();
						if(stdoutReader != null) {
							pS.setCharacterStream(14, stdoutReader);
						}
						else {
							pS.setNull(14, Types.CLOB);
						}

						Reader stderrReader = junitTestDetailInfo.getStderr();
						if(stderrReader != null) {
							pS.setCharacterStream(15, stderrReader);
						}
						else {
							pS.setNull(15, Types.CLOB);
						}

					}
					else {
						pS.setNull(12, Types.VARCHAR);
						pS.setNull(13, Types.VARCHAR);
						pS.setNull(14, Types.CLOB);
						pS.setNull(15, Types.CLOB);
					}
					pS.executeUpdate();
				}
				connection.commit();
			}
			finally {
				pS.close();
				connection.rollback();
				connection.close();
			}
		}
	}

	private String truncate(String str, int limit) {
		if(str != null && str.length() > limit) {
			str = str.substring(0, limit);
		}
		return str;
	}

	private List<JUnitTestInfo> readTests(ResultSet rS) throws SQLException {
		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		if(rS != null) {
			while(rS.next()) {
				JUnitTestInfo junitTestInfo = new JUnitTestInfo();
				junitTestInfo.setId(rS.getLong(1));
				junitTestInfo.setProjectName(rS.getString(2));
				junitTestInfo.setBuildId(rS.getString(3));
				junitTestInfo.setBuildNumber(rS.getInt(4));
				junitTestInfo.setModuleName(rS.getString(5));
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

	private List<JUnitSummaryInfo> readSummary(ResultSet rS, int limit) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		if(rS != null) {
			int count = 0;
			while((limit < 0 || count < limit) && rS.next()) {
				JUnitSummaryInfo junitSummaryInfo = new JUnitSummaryInfo();
				junitSummaryInfo.setBuildId(rS.getString(1));
				junitSummaryInfo.setBuildNumber(rS.getInt(2));
				junitSummaryInfo.setProjectName(rS.getString(3));
				junitSummaryInfo.setModuleName(rS.getString(4));
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

	public List<JUnitTestInfo> queryTestsByProject(String projectName, int buildNumber) throws SQLException {
		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setInt(2, buildNumber);

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

	public List<JUnitTestInfo> queryTestsByModule(String projectName, int buildNumber, String moduleName) throws SQLException {

		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setInt(2, buildNumber);
				pS.setString(3, moduleName);

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

	public List<JUnitTestInfo> queryTestsByPackage(String projectName, int buildNumber, String moduleName, String packageName) throws SQLException {

		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_PACKAGE_NAME_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setInt(2, buildNumber);
				pS.setString(3, moduleName);
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

	public List<JUnitTestInfo> queryTestsByClass(String projectName, int buildNumber, String moduleName, String packageName, String className) throws SQLException {

		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_PACKAGE_NAME_CLASS_NAME_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setInt(2, buildNumber);
				pS.setString(3, moduleName);
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

	public JUnitTestInfo queryTestCase(String projectName, int buildNumber, String moduleName, String packageName, String className, String caseName) throws SQLException {

		JUnitTestInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_BUILD_NUMBER_MODULE_NAME_CLASS_NAME_CASE_NAME_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setInt(2, buildNumber);
				pS.setString(3, moduleName);
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

	public JUnitTestInfo queryTestCaseForBuildPriorTo(String projectName, int buildNumber, String moduleName, String packageName, String className,
			String caseName) throws SQLException {

		JUnitTestInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_BY_PROJECT_NAME_PRIOR_BUILD_NUMBER_MODULE_NAME_CLASS_NAME_CASE_NAME_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
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

	public List<JUnitTestInfo> getTestCaseHistory(String projectName, String moduleName, String packageName, String className, String caseName) throws SQLException {

		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_TEST_CASE_HISTORY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, moduleName);
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

	public List<JUnitSummaryInfo> summarizeTestCaseHistory(String projectName, String moduleName, String packageName, String className,
			String caseName, int limit) throws SQLException {

		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CASE_HISTORY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, moduleName);
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

	public JUnitSummaryInfo summarizeTestCaseForBuild(int buildNumber, String projectName, String moduleName, String packageName, String className,
			String caseName) throws SQLException {

		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CASE_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);
				pS.setString(5, className);
				pS.setString(6, caseName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitSummaryInfo summarizeTestCaseForBuildPriorTo(int buildNumber, String projectName, String moduleName, String packageName,
			String className, String caseName) throws SQLException {

		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CASE_FOR_BUILD_PRIOR_TO_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);
				pS.setString(5, className);
				pS.setString(6, caseName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public List<JUnitSummaryInfo> summarizeTestClassHistory(String projectName, String moduleName, String packageName, String className, int limit) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CLASS_HISTORY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, moduleName);
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

	public List<JUnitTestInfo> fetchTestClassChildrenForBuild(int buildNumber, String projectName, String moduleName, String packageName,
			String className) throws SQLException {
		List<JUnitTestInfo> result = new ArrayList<JUnitTestInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_CLASS_CHILDREN_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
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

	public JUnitSummaryInfo summarizeTestClassForBuild(int buildNumber, String projectName, String moduleName, String packageName, String className) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CLASS_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);
				pS.setString(5, className);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitSummaryInfo summarizeTestClassForBuildPriorTo(int buildNumber, String projectName, String moduleName, String packageName,
			String className) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_CLASS_FOR_BUILD_PRIOR_TO_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);
				pS.setString(5, className);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public List<JUnitSummaryInfo> summarizeTestPackageHistory(String projectName, String moduleName, String packageName, int limit) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_PACKAGE_HISTORY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, moduleName);
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

	public List<JUnitSummaryInfo> fetchTestPackageChildrenForBuild(int buildNumber, String projectName, String moduleName, String packageName) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PACKAGE_CHILDREN_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);

				ResultSet rS = pS.executeQuery();
				try {
					result = readSummary(rS, -1);
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

	public void summarizeTestPackagesForBuild(int buildNumber, String projectName, String moduleName) throws SQLException {
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_PACKAGES_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.executeUpdate();
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
	}

	public JUnitSummaryInfo fetchTestPackageSummaryForBuild(int buildNumber, String projectName, String moduleName, String packageName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PACKAGE_SUMMARY_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitSummaryInfo fetchTestPackageSummaryForBuildPriorTo(int buildNumber, String projectName, String moduleName, String packageName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PACKAGE_SUMMARY_FOR_BUILD_PRIOR_TO_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public List<JUnitSummaryInfo> fetchTestModuleSummaryHistory(String projectName, String moduleName, int limit) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_FETCH_TEST_MODULE_SUMMARY_HISTORY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, moduleName);

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

	public List<JUnitSummaryInfo> fetchTestModuleChildrenForBuild(int buildNumber, String projectName, String moduleName) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_MODULE_CHILDREN_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);

				ResultSet rS = pS.executeQuery();
				try {
					result = readSummary(rS, -1);
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

	public void summarizeTestModuleForBuild(int buildNumber, String projectName, String moduleName) throws SQLException {
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_MODULE_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);

				pS.executeUpdate();

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
	}

	public JUnitSummaryInfo fetchTestModuleSummaryForBuildPriorTo(int buildNumber, String projectName, String moduleName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_FETCH_TEST_MODULE_SUMMARY_FOR_BUILD_PRIOR_TO_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitSummaryInfo fetchTestModuleSummaryForBuild(int buildNumber, String projectName, String moduleName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_FETCH_TEST_MODULE_SUMMARY_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitSummaryInfo fetchTestModuleSummaryForBuildNoLaterThan(int buildNumber, String projectName, String moduleName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_MODULE_SUMMARY_FOR_BUILD_NO_LATER_THAN_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public List<JUnitSummaryInfo> summarizeTestProjectHistory(String projectName, int limit) throws SQLException {
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

	public List<JUnitSummaryInfo> fetchTestProjectChildrenForBuild(int buildNumber, String projectName) throws SQLException {
		List<JUnitSummaryInfo> result = new ArrayList<JUnitSummaryInfo>();
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PROJECT_CHILDREN_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);

				ResultSet rS = pS.executeQuery();
				try {
					result = readSummary(rS, -1);
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

	public void summarizeTestProjectForBuild(int buildNumber, String projectName) throws SQLException {
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SUMMARIZE_TEST_PROJECT_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.executeUpdate();
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
	}

	public JUnitSummaryInfo fetchTestProjectSummaryForBuild(int buildNumber, String projectName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PROJECT_SUMMARY_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitSummaryInfo fetchTestProjectSummaryForBuildPriorTo(int buildNumber, String projectName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PROJECT_SUMMARY_FOR_BUILD_PRIOR_TO_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitSummaryInfo fetchTestProjectSummaryForBuildNoLaterThan(int buildNumber, String projectName) throws SQLException {
		JUnitSummaryInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PROJECT_SUMMARY_FOR_BUILD_NO_LATER_THAN_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);

				ResultSet rS = pS.executeQuery();
				try {
					List<JUnitSummaryInfo> junitSummaryInfoList = readSummary(rS, 1);
					if(!junitSummaryInfoList.isEmpty()) {
						result = junitSummaryInfoList.get(0);
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

	public JUnitTestDetailInfo readTestDetail(int buildNumber, String projectName, String moduleName, String packageName, String className,
			String caseName, ReaderWriter stdoutReaderWriter, ReaderWriter stderrReaderWriter) throws IOException, SQLException {

		JUnitTestDetailInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_SELECT_TESTCASE_DETAIL_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);
				pS.setString(5, className);
				pS.setString(6, caseName);
				ResultSet rS = pS.executeQuery();
				try {
					if(rS.next()) {
						result = new JUnitTestDetailInfo();
						result.setErrorMessage(rS.getString(1));
						result.setErrorStackTrace(rS.getString(2));

						if(stdoutReaderWriter != null) {
							Clob stdoutClob = rS.getClob(3);
							if(stdoutClob != null) {
								writeClobTo(stdoutClob, stdoutReaderWriter.getWriter());
								result.setStdout(stdoutReaderWriter.getReader());
							}
						}

						if(stderrReaderWriter != null) {
							Clob stderrClob = rS.getClob(4);
							if(stderrClob != null) {
								writeClobTo(stderrClob, stderrReaderWriter.getWriter());
								result.setStderr(stderrReaderWriter.getReader());
							}
						}
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

	public JUnitMetricsInfo fetchTestCaseMetrics(int buildNumber, String projectName, String moduleName, String packageName, String className,
			String caseName) throws SQLException {
		JUnitMetricsInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_CASE_METRICS_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);
				pS.setString(5, className);
				pS.setString(6, caseName);

				ResultSet rS = pS.executeQuery();
				try {
					result = readMetrics(rS);
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

	public JUnitMetricsInfo fetchTestClassMetrics(int buildNumber, String projectName, String moduleName, String packageName, String className) throws SQLException {
		JUnitMetricsInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_CLASS_METRICS_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);
				pS.setString(5, className);

				ResultSet rS = pS.executeQuery();
				try {
					result = readMetrics(rS);
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

	public JUnitMetricsInfo fetchTestPackageMetrics(int buildNumber, String projectName, String moduleName, String packageName) throws SQLException {
		JUnitMetricsInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PACKAGE_METRICS_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);
				pS.setString(4, packageName);

				ResultSet rS = pS.executeQuery();
				try {
					result = readMetrics(rS);
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

	public JUnitMetricsInfo fetchTestModuleMetrics(int buildNumber, String projectName, String moduleName) throws SQLException {
		JUnitMetricsInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_MODULE_METRICS_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);
				pS.setString(3, moduleName);

				ResultSet rS = pS.executeQuery();
				try {
					result = readMetrics(rS);
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

	public JUnitMetricsInfo fetchTestProjectMetrics(int buildNumber, String projectName) throws SQLException {
		JUnitMetricsInfo result = null;
		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_TEST_PROJECT_METRICS_FOR_BUILD_QUERY);
			try {
				pS.setInt(1, buildNumber);
				pS.setString(2, projectName);

				ResultSet rS = pS.executeQuery();
				try {
					result = readMetrics(rS);
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

	public String getProperty(String projectName, String propertyName) throws SQLException {
		String result = null;
		if(projectName != null && propertyName != null) {
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_PROPERTY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, propertyName);
					ResultSet rS = pS.executeQuery();
					if(rS.next()) {
						result = rS.getString(1);
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
		}
		return result;
	}

	public boolean hasProperty(String projectName, String propertyName) throws SQLException {
		boolean result = false;
		if(projectName != null && propertyName != null) {
			Connection connection = getConnection();
			try {
				PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_FETCH_PROPERTY);
				try {
					pS.setString(1, projectName);
					pS.setString(2, propertyName);
					ResultSet rS = pS.executeQuery();
					if(rS.next()) {
						result = true;
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
		}
		return result;
	}

	public void setProperty(String projectName, String propertyName, String value) throws SQLException {
		if(projectName != null && propertyName != null) {
			Connection connection = getConnection();
			try {
				if(!hasProperty(projectName, propertyName)) {
					PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_INSERT_PROPERTY);
					try {
						pS.setString(1, projectName);
						pS.setString(2, propertyName);
						pS.setString(3, value);
						pS.executeUpdate();
					}
					finally {
						pS.close();
					}
				}
				else {
					PreparedStatement pS = connection.prepareStatement(JUNIT_TESTS_UPDATE_PROPERTY);
					try {
						pS.setString(1, value);
						pS.setString(2, projectName);
						pS.setString(3, propertyName);
						pS.executeUpdate();
					}
					finally {
						pS.close();
					}
				}
				connection.commit();
			}
			finally {
				connection.rollback();
				connection.close();
			}
		}
	}

	public void compactDB(String projectName, List<String> activeBuildIds) throws SQLException {
		boolean isCompacting = false;
		long dbSizeThreshold = 0;

		Connection connection = getConnection();
		try {
			PreparedStatement pS = connection.prepareStatement(CLEAR_JUNIT_ACTIVE_BUILDS_QUERY);
			try {
				pS.setString(1, projectName);
				pS.executeUpdate();
			}
			finally {
				pS.close();
			}
			connection.commit();

			if(activeBuildIds != null) {
				pS = connection.prepareStatement(JUNIT_ACTIVE_BUILDS_TABLE_INSERT_QUERY);
				try {
					pS.setString(1, projectName);

					for(String buildId: activeBuildIds) {
						pS.setString(2, buildId);
						pS.executeUpdate();
					}
				}
				finally {
					pS.close();
				}
			}
			connection.commit();

			pS = connection.prepareStatement(COMPACT_JUNIT_PROJECT_SUMMARY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, projectName);
				pS.executeUpdate();
			}
			finally {
				pS.close();
			}
			connection.commit();

			pS = connection.prepareStatement(COMPACT_JUNIT_MODULE_SUMMARY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, projectName);
				pS.executeUpdate();
			}
			finally {
				pS.close();
			}
			connection.commit();

			pS = connection.prepareStatement(COMPACT_JUNIT_PACKAGE_SUMMARY_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, projectName);
				pS.executeUpdate();
			}
			finally {
				pS.close();
			}
			connection.commit();

			pS = connection.prepareStatement(COMPACT_JUNIT_TESTS_QUERY);
			try {
				pS.setString(1, projectName);
				pS.setString(2, projectName);
				pS.executeUpdate();
			}
			finally {
				pS.close();
			}
			connection.commit();

			long dbSize = FileUtils.sizeOfDirectory(new File(getDatabasePath()));

			String dbSizeThresholdStr = getProperty("_defaultSettings_", "dbSizeThreshold");
			if(dbSizeThresholdStr != null) {
				try {
					dbSizeThreshold = Long.parseLong(dbSizeThresholdStr);
				}
				catch(NumberFormatException nFE) {
					LOGGER.log(Level.WARNING, nFE.getMessage(), nFE);
				}
			}

			if(dbSize > dbSizeThreshold) {
				isCompacting = true;
				connection.setAutoCommit(true);

				CallableStatement cs = connection.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, ?)");
				cs.setString(1, "APP");
				cs.setString(2, "JUNIT_TESTS");
				cs.setShort(3, (short)1);
				cs.execute();

				cs.setString(1, "APP");
				cs.setString(2, "JUNIT_PACKAGE_SUMMARY");
				cs.setShort(3, (short)1);
				cs.execute();

				cs.setString(1, "APP");
				cs.setString(2, "JUNIT_MODULE_SUMMARY");
				cs.setShort(3, (short)1);
				cs.execute();

				cs.setString(1, "APP");
				cs.setString(2, "JUNIT_PROJECT_SUMMARY");
				cs.setShort(3, (short)1);
				cs.execute();

			}

		}
		finally {
			connection.rollback();
			connection.close();
		}

		if(isCompacting) {
			long newDbSize = FileUtils.sizeOfDirectory(new File(getDatabasePath()));
			dbSizeThreshold = newDbSize + 1024 * 1024 * 500;
			setProperty("_defaultSettings_", "dbSizeThreshold", Long.toString(dbSizeThreshold));
		}
	}

	private JUnitMetricsInfo readMetrics(ResultSet rS) throws SQLException {
		JUnitMetricsInfo result = null;
		if(rS.next()) {
			result = new JUnitMetricsInfo();
			result.setTotalCount(rS.getLong(1));
			result.setSuccessCount(rS.getLong(2));
			result.setFailCount(rS.getLong(3));
			result.setErrorCount(rS.getLong(4));
			result.setSkipCount(rS.getLong(5));
		}
		return result;
	}

	private void writeClobTo(Clob clob, Writer writer) throws SQLException, IOException {
		Reader reader = clob.getCharacterStream();
		char[] buffer = new char[1024];
		int charsRead = 0;
		while((charsRead = reader.read(buffer)) > 0) {
			writer.write(buffer, 0, charsRead);
		}
		writer.flush();
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
