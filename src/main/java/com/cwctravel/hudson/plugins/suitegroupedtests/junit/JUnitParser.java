package com.cwctravel.hudson.plugins.suitegroupedtests.junit;

import hudson.tasks.test.TestObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitTestDetailInfo;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitTestInfo;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.io.IOUtil;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.io.ReaderWriter;

public class JUnitParser implements ContentHandler {
	private static final Logger LOGGER = Logger.getLogger(JUnitParser.class.getName());

	private static final int STATE_DOCUMENT_START = 0;
	private static final int STATE_TEST_SUITES_START = 1;
	private static final int STATE_TEST_SUITE_START = 2;
	private static final int STATE_TEST_SUITE_ERROR_START = 3;
	private static final int STATE_TEST_SUITE_FAILURE_START = 4;
	private static final int STATE_TEST_SUITE_SKIP_START = 5;
	private static final int STATE_TEST_SUITE_SYSOUT_START = 6;
	private static final int STATE_TEST_SUITE_SYSERR_START = 7;
	private static final int STATE_TEST_SUITE_TEST_CASE_START = 8;
	private static final int STATE_TEST_SUITE_TEST_CASE_SYSERR_START = 9;
	private static final int STATE_TEST_SUITE_TEST_CASE_SYSOUT_START = 10;
	private static final int STATE_TEST_SUITE_TEST_CASE_SKIP_START = 11;
	private static final int STATE_TEST_SUITE_TEST_CASE_FAILURE_START = 12;
	private static final int STATE_TEST_SUITE_TEST_CASE_ERROR_START = 13;
	private static final int STATE_IGNORED = 14;

	private static final SimpleDateFormat JUNIT_DATE_FORMAT = new SimpleDateFormat("yyyy-mm-dd'T'hh:MM:ss");

	private final JUnitDB junitDB;

	private Deque<Integer> stateStack;

	private int buildNumber;
	private String buildId;
	private String projectName;

	private String reportFileName;

	private int currentSuiteStatus;
	private int currentSuiteTestCaseIndex;

	private long currentSuiteDuration;
	private long currentSuiteTimestamp;

	private String currentSuiteName;
	private String currentSuiteErrorMessage;
	private String currentSuiteErrorStackTrace;

	private ReaderWriter currentSuiteStdout;
	private ReaderWriter currentSuiteStderr;

	private int currentTestCaseStatus;

	private long currentTestCaseDuration;

	private String currentTestPackageName;
	private String currentTestClassName;
	private String currentTestCaseName;
	private String currentTestCaseErrorMessage;
	private String currentTestCaseErrorStackTrace;

	private ReaderWriter currentTestCaseStdout;
	private ReaderWriter currentTestCaseStderr;

	private List<JUnitTestInfo> junitTestCases;
	private List<ReaderWriter> readerWriters;

	public JUnitParser(JUnitDB junitDB) {
		this.junitDB = junitDB;
	}

	public void parse(int buildNumber, String buildId, String projectName, File xmlReport) throws SAXException, ParserConfigurationException, IOException, SQLException {
		this.buildNumber = buildNumber;
		this.buildId = buildId;
		this.projectName = projectName;

		this.junitTestCases = new ArrayList<JUnitTestInfo>();
		this.stateStack = new ArrayDeque<Integer>();
		this.readerWriters = new ArrayList<ReaderWriter>();

		this.reportFileName = xmlReport.getAbsolutePath();

		SAXParserFactory spf = SAXParserFactory.newInstance();
		spf.setNamespaceAware(false);
		SAXParser saxParser = spf.newSAXParser();
		XMLReader xmlReader = saxParser.getXMLReader();
		xmlReader.setContentHandler(this);
		FileReader fR = new FileReader(xmlReport);
		try {
			xmlReader.parse(new InputSource(fR));
			persistTestCases();
		}
		finally {
			fR.close();
		}
	}

	public void setDocumentLocator(Locator locator) {
		// TODO Auto-generated method stub

	}

	public void startDocument() throws SAXException {
		stateStack.push(STATE_DOCUMENT_START);

	}

	public void endDocument() throws SAXException {
		stateStack.pop();
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		// TODO Auto-generated method stub

	}

	public void endPrefixMapping(String prefix) throws SAXException {
		// TODO Auto-generated method stub

	}

	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		Integer currentState = stateStack.peek();
		if(currentState == STATE_DOCUMENT_START && "testsuites".equals(qName)) {
			stateStack.push(STATE_TEST_SUITES_START);
		}
		else if((currentState == STATE_DOCUMENT_START || currentState == STATE_TEST_SUITES_START) && "testsuite".equals(qName)) {
			stateStack.push(STATE_TEST_SUITE_START);
			currentSuiteName = atts.getValue("name");
			if(currentSuiteName == null) {
				currentSuiteName = '(' + reportFileName + ')';
			}
			else {
				String packageName = atts.getValue("package");
				if(packageName != null && packageName.length() > 0) {
					currentSuiteName = packageName + '.' + currentSuiteName;
				}
				currentSuiteName = TestObject.safe(currentSuiteName);
			}
			currentSuiteTimestamp = parseDate(atts.getValue("timestamp"));
			currentSuiteDuration = parseTime(atts.getValue("time"));
		}
		else if(currentState == STATE_TEST_SUITE_START && "error".equals(qName)) {
			currentSuiteErrorMessage = atts.getValue("message");
			currentSuiteStatus = JUnitTestInfo.STATUS_ERROR;
			stateStack.push(STATE_TEST_SUITE_ERROR_START);
		}
		else if(currentState == STATE_TEST_SUITE_START && "failure".equals(qName)) {
			currentSuiteErrorMessage = atts.getValue("message");
			currentSuiteStatus = JUnitTestInfo.STATUS_FAIL;
			stateStack.push(STATE_TEST_SUITE_FAILURE_START);
		}
		else if(currentState == STATE_TEST_SUITE_START && "skipped".equals(qName)) {
			currentSuiteStatus = JUnitTestInfo.STATUS_SKIP;
			stateStack.push(STATE_TEST_SUITE_SKIP_START);
		}
		else if(currentState == STATE_TEST_SUITE_START && "system-out".equals(qName)) {
			stateStack.push(STATE_TEST_SUITE_SYSOUT_START);
		}
		else if(currentState == STATE_TEST_SUITE_START && "system-err".equals(qName)) {
			stateStack.push(STATE_TEST_SUITE_SYSERR_START);
		}
		else if(currentState == STATE_TEST_SUITE_START && "testcase".equals(qName)) {
			currentTestCaseStatus = JUnitTestInfo.STATUS_SUCCESS;
			currentTestClassName = atts.getValue("classname");
			currentTestCaseName = atts.getValue("name");
			if(currentTestClassName == null && currentTestCaseName != null) {
				int indexOfDot = currentTestCaseName.lastIndexOf('.');
				if(indexOfDot >= 0) {
					currentTestClassName = currentTestCaseName.substring(0, indexOfDot);
					currentTestCaseName = currentTestCaseName.substring(indexOfDot + 1);
				}
			}
			if(currentTestClassName != null) {
				int indexOfDot = currentTestClassName.lastIndexOf('.');
				if(indexOfDot >= 0) {
					currentTestPackageName = currentTestClassName.substring(0, indexOfDot);
					currentTestClassName = currentTestClassName.substring(indexOfDot + 1);
				}
			}
			currentTestCaseDuration = parseTime(atts.getValue("time"));
			stateStack.push(STATE_TEST_SUITE_TEST_CASE_START);

		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_START && "error".equals(qName)) {
			currentTestCaseErrorMessage = atts.getValue("message");
			currentTestCaseStatus = JUnitTestInfo.STATUS_ERROR;
			stateStack.push(STATE_TEST_SUITE_TEST_CASE_ERROR_START);
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_START && "failure".equals(qName)) {
			currentTestCaseErrorMessage = atts.getValue("message");
			currentTestCaseStatus = JUnitTestInfo.STATUS_FAIL;
			stateStack.push(STATE_TEST_SUITE_TEST_CASE_FAILURE_START);
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_START && "skipped".equals(qName)) {
			currentTestCaseStatus = JUnitTestInfo.STATUS_SKIP;
			stateStack.push(STATE_TEST_SUITE_TEST_CASE_SKIP_START);
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_START && "system-out".equals(qName)) {
			stateStack.push(STATE_TEST_SUITE_TEST_CASE_SYSOUT_START);
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_START && "system-err".equals(qName)) {
			stateStack.push(STATE_TEST_SUITE_TEST_CASE_SYSERR_START);
		}
		else {
			stateStack.push(STATE_IGNORED);
		}

	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		Integer currentState = stateStack.peek();
		if(currentState == STATE_TEST_SUITE_ERROR_START || currentState == STATE_TEST_SUITE_FAILURE_START) {
			currentSuiteErrorStackTrace = new String(ch, start, length);
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_ERROR_START || currentState == STATE_TEST_SUITE_TEST_CASE_FAILURE_START) {
			currentTestCaseErrorStackTrace = new String(ch, start, length);
		}
		else if(currentState == STATE_TEST_SUITE_SYSOUT_START) {
			try {
				currentSuiteStdout = IOUtil.createReaderWriter(ch, start, length);
			}
			catch(IOException iE) {
				LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
			}
		}
		else if(currentState == STATE_TEST_SUITE_SYSERR_START) {
			try {
				currentSuiteStderr = IOUtil.createReaderWriter(ch, start, length);
			}
			catch(IOException iE) {
				LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
			}
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_SYSOUT_START) {
			try {
				currentTestCaseStdout = IOUtil.createReaderWriter(ch, start, length);
			}
			catch(IOException iE) {
				LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
			}
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_SYSERR_START) {
			try {
				currentTestCaseStderr = IOUtil.createReaderWriter(ch, start, length);
			}
			catch(IOException iE) {
				LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
			}
		}
	}

	public void endElement(String uri, String localName, String qName) throws SAXException {
		Integer currentState = stateStack.peek();
		if(currentState == STATE_TEST_SUITE_START && "testsuite".equals(qName)) {
			insertTestCase(buildId, buildNumber, projectName, currentSuiteName, "<init>", "<init>", "<init>", currentSuiteTestCaseIndex, currentSuiteStatus, currentSuiteTimestamp, currentSuiteDuration, currentSuiteErrorMessage, currentSuiteErrorStackTrace, currentSuiteStdout, currentSuiteStderr);
			currentSuiteTestCaseIndex = 0;
			currentSuiteStdout = null;
			currentSuiteStderr = null;
		}
		else if(currentState == STATE_TEST_SUITE_TEST_CASE_START && "testcase".equals(qName)) {
			insertTestCase(buildId, buildNumber, projectName, currentSuiteName, currentTestPackageName, currentTestClassName, currentTestCaseName, currentSuiteTestCaseIndex++, currentTestCaseStatus, currentSuiteTimestamp, currentTestCaseDuration, currentTestCaseErrorMessage, currentTestCaseErrorStackTrace, currentTestCaseStdout, currentTestCaseStderr);
			currentTestCaseStdout = null;
			currentTestCaseStderr = null;

		}

		stateStack.pop();
	}

	private long parseDate(String value) {
		try {
			return JUNIT_DATE_FORMAT.parse(value).getTime();

		}
		catch(ParseException pE) {
			LOGGER.log(Level.WARNING, pE.getMessage(), pE);
		}
		return 0;
	}

	private long parseTime(String value) {
		if(value != null) {
			value = value.replace(",", "");
			try {
				return (long)(Float.parseFloat(value) * 1000);
			}
			catch(NumberFormatException e) {
				try {
					return (long)(new DecimalFormat().parse(value).floatValue() * 1000);
				}
				catch(ParseException pE) {
					LOGGER.log(Level.WARNING, pE.getMessage(), pE);
				}
			}
		}
		return 0;
	}

	private void insertTestCase(String buildId, int buildNumber, String projectName, String suiteName, String packageName, String className,
			String caseName, int index, int status, long startTime, long duration, String errorMessage, String errorStackTrace, ReaderWriter stdout,
			ReaderWriter stderr) {
		JUnitTestInfo junitTestInfo = new JUnitTestInfo();
		junitTestInfo.setBuildId(buildId);
		junitTestInfo.setBuildNumber(buildNumber);
		junitTestInfo.setProjectName(projectName);
		junitTestInfo.setPackageName(packageName == null ? "<none>" : packageName);
		junitTestInfo.setSuiteName(suiteName);
		junitTestInfo.setClassName(className == null ? "<none>" : className);
		junitTestInfo.setCaseName(caseName);
		junitTestInfo.setIndex(index);
		junitTestInfo.setStatus(status);
		junitTestInfo.setStartTime(startTime);
		junitTestInfo.setDuration(duration);

		try {
			JUnitTestDetailInfo junitTestDetailInfo = new JUnitTestDetailInfo();
			junitTestDetailInfo.setErrorMessage(errorMessage);
			junitTestDetailInfo.setErrorStackTrace(errorStackTrace);

			if(stdout != null) {
				junitTestDetailInfo.setStdout(stdout.getReader());
			}

			if(stderr != null) {
				junitTestDetailInfo.setStderr(stderr.getReader());
			}

			junitTestInfo.setDetail(junitTestDetailInfo);
		}
		catch(IOException iE) {
			LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
		}

		if(junitTestCases.size() > 100) {
			try {
				persistTestCases();
			}
			catch(IOException iE) {
				LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		else {
			junitTestCases.add(junitTestInfo);
			if(stdout != null) {
				readerWriters.add(stdout);
			}
			if(stderr != null) {
				readerWriters.add(stderr);
			}
		}

	}

	private void persistTestCases() throws IOException, SQLException {
		try {
			junitDB.insertTests(junitTestCases);
		}
		finally {
			junitTestCases.clear();
		}

		try {
			for(ReaderWriter readerWriter: readerWriters) {
				readerWriter.release();
			}
		}
		finally {
			readerWriters.clear();
		}

	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {

	}

	public void processingInstruction(String target, String data) throws SAXException {}

	public void skippedEntity(String name) throws SAXException {

	}

	/*public static void main(String[] args) throws Exception {
		JUnitDB junitDB = new JUnitDB("D:/Temp");

		// "D:/TEST-com.cwctravel.test.AllTests.xml" == true;

		JUnitParser junitParser = new JUnitParser(junitDB);
		junitParser.parse(0, "test-build-id", "test-project", new File("D:/logs/TEST-com.cwctravel.test.AllTests.xml"));
	}*/
}
