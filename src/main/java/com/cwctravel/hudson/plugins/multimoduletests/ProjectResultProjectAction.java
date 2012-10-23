package com.cwctravel.hudson.plugins.multimoduletests;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.test.TestResultProjectAction;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;

import com.cwctravel.hudson.plugins.multimoduletests.junit.ModuleResult;
import com.cwctravel.hudson.plugins.multimoduletests.junit.ProjectResult;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitSummaryInfo;

public class ProjectResultProjectAction extends TestResultProjectAction implements StaplerProxy {
	private static final Logger LOGGER = Logger.getLogger(ProjectResultProjectAction.class.getName());

	public ProjectResultProjectAction(AbstractProject<?, ?> project) {
		super(project);
	}

	@Override
	public String getUrlName() {
		return "testReport";
	}

	public Collection<ModuleResult> getModules() {
		ProjectResultBuildAction action = getLastTestResultAction();
		if(action != null) {
			ProjectResult projectResult = action.getResultAsProjectResult();
			if(projectResult != null)
				return projectResult.getChildren();
		}
		return Collections.EMPTY_LIST;
	}

	@Override
	public ProjectResultBuildAction getLastTestResultAction() {
		final AbstractBuild<?, ?> tb = project.getLastSuccessfulBuild();

		AbstractBuild<?, ?> b = project.getLastBuild();

		while(b != null) {
			ProjectResultBuildAction a = b.getAction(ProjectResultBuildAction.class);
			if(a != null) {
				return a;
			}
			if(b == tb) {
				// if even the last successful build didn't produce the test result,
				// that means we just don't have any tests configured.
				return null;
			}
			b = b.getPreviousBuild();
		}

		return null;
	}

	public ProjectResult getLastProjectResult() {
		ProjectResultBuildAction projectResultAction = getLastTestResultAction();
		if(projectResultAction != null) {
			return projectResultAction.getResult();
		}
		return null;
	}

	public ProjectResult getProject() {
		return getLastProjectResult();
	}

	public Object getTarget() {
		StaplerRequest request = Stapler.getCurrentRequest();
		if(StringUtils.isEmpty(request.getRestOfPath())) {
			return getModules();
		}
		return this;
	}

	public TrendGraph getTrendGraph(String moduleName) {
		ProjectResultBuildAction action = getLastTestResultAction();
		int MAX_HISTORY = 5000; // totally arbitrary, yep
		if(action != null) {
			ProjectResult projectResult = action.getResultAsProjectResult();
			if(projectResult != null) {
				if(projectResult.getModuleNames().contains(moduleName)) {
					AbstractProject<?, ?> project = projectResult.getOwner().getParent();
					JUnitDB junitDB;
					try {
						junitDB = new JUnitDB(project.getRootDir().getAbsolutePath());
						List<JUnitSummaryInfo> historyList = junitDB.summarizeTestModuleHistory(project.getName(), moduleName, MAX_HISTORY);
						return new TrendGraph("/testReport/", "/" + moduleName + "/", "count", historyList);
					}
					catch(SQLException e) {
						LOGGER.warning("Couldn't find the right result group for a trend graph for suite '" + moduleName + "'");
					}
				}
			}
		}
		LOGGER.warning("Couldn't find the right result group for a trend graph for suite '" + moduleName + "'");
		return null;
	}

}