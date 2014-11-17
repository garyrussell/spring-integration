/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @since 4.1
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class InboundToOutboundTests {

	private final static String tmpDir = System.getProperty("java.io.tmpdir");

	@Autowired
	private SourcePollingChannelAdapter inbound;

	@Test
	public void test() throws Exception {
		File inDir = new File(tmpDir + File.pathSeparator + "sifiletestIn");
		inDir.mkdir();
		File in = new File(inDir, "foo.txt");
		File outDir = new File(tmpDir + File.pathSeparator + "sifiletestOut");
		outDir.mkdir();
		File out = new File(outDir, "foo.txt");
		in.delete();
		out.delete();

		assertTrue(in.createNewFile());
		inbound.start();
		int n = 0;
		while (n++ < 100 && !out.exists()) {
			Thread.sleep(100);
		}
		assertTrue(out.exists());
		assertFalse(in.exists());
		System.out.println(in);
	}


	public static class FireOnceTrigger implements Trigger {

		private volatile boolean done;

		@Override
		public Date nextExecutionTime(TriggerContext triggerContext) {
			if (done) {
				return null;
			};
			done = true;
			return new Date();
		}

	}

}
