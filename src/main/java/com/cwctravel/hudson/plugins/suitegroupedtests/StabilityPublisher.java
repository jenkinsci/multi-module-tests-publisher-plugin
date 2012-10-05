package com.cwctravel.hudson.plugins.suitegroupedtests;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.tasks.junit.TestAction;
import hudson.tasks.test.TestObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import com.cwctravel.hudson.plugins.suitegroupedtests.SuiteGroupResultAction.Data;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.SuiteGroupResult;

public class StabilityPublisher extends TestDataPublisher {

	@Override
	public Data getTestData(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, SuiteGroupResult testResult) throws IOException, InterruptedException {
		return new Data() {
			@Override
			public List<? extends TestAction> getTestAction(TestObject testObject) {
				List<StabilityAction> result = new ArrayList<StabilityAction>();
				result.add(new StabilityAction(testObject));
				return result;
			}

		};
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
		@Override
		public String getDisplayName() {
			return "Publish Test Stability Report";
		}

		@Override
		public TestDataPublisher newInstance(StaplerRequest req, JSONObject formData) throws hudson.model.Descriptor.FormException {
			return new StabilityPublisher();
		}

	}
}
