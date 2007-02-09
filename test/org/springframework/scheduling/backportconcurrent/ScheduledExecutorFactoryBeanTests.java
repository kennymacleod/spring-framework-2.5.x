/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.scheduling.backportconcurrent;

import edu.emory.mathcs.backport.java.util.concurrent.RejectedExecutionHandler;
import edu.emory.mathcs.backport.java.util.concurrent.ScheduledExecutorService;
import edu.emory.mathcs.backport.java.util.concurrent.ThreadFactory;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.easymock.MockControl;

import org.springframework.core.task.NoOpRunnable;

/**
 * @author Rick Evans
 * @author Juergen Hoeller
 */
public class ScheduledExecutorFactoryBeanTests extends TestCase {

	public void testThrowsExceptionIfPoolSizeIsLessThanZero() throws Exception {
		try {
			ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean();
			factory.setPoolSize(-1);
			factory.setScheduledExecutorTasks(new ScheduledExecutorTask[]{
				new ScheduledExecutorFactoryBeanTests.NoOpScheduledExecutorTask()
			});
			factory.afterPropertiesSet();
			fail("Pool size less than zero");
		}
		catch (IllegalArgumentException expected) {
		}
	}

	public void testShutdownIsPropagatedToTheExecutorOnDestroy() throws Exception {
		MockControl mockScheduledExecutorService = MockControl.createNiceControl(ScheduledExecutorService.class);
		final ScheduledExecutorService executor = (ScheduledExecutorService) mockScheduledExecutorService.getMock();
		executor.shutdown();
		mockScheduledExecutorService.setVoidCallable();
		mockScheduledExecutorService.replay();

		ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean() {
			protected ScheduledExecutorService createExecutor(int poolSize, ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
				return executor;
			}
		};
		factory.setScheduledExecutorTasks(new ScheduledExecutorTask[]{
			new ScheduledExecutorFactoryBeanTests.NoOpScheduledExecutorTask()
		});
		factory.afterPropertiesSet();
		factory.destroy();

		mockScheduledExecutorService.verify();
	}

	public void testOneTimeExecutionIsSetupAndFiresCorrectly() throws Exception {

		MockControl mockRunnable = MockControl.createControl(Runnable.class);
		Runnable runnable = (Runnable) mockRunnable.getMock();
		runnable.run();
		mockRunnable.setVoidCallable();
		mockRunnable.replay();

		ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean();
		factory.setScheduledExecutorTasks(new ScheduledExecutorTask[]{
			new ScheduledExecutorTask(runnable)
		});
		factory.afterPropertiesSet();
		pauseToLetTaskStart(1);
		factory.destroy();

		mockRunnable.verify();
	}

	public void testFixedRepeatedExecutionIsSetupAndFiresCorrectly() throws Exception {

		MockControl mockRunnable = MockControl.createControl(Runnable.class);
		Runnable runnable = (Runnable) mockRunnable.getMock();
		runnable.run();
		mockRunnable.setVoidCallable();
		runnable.run();
		mockRunnable.setVoidCallable();
		mockRunnable.replay();

		ScheduledExecutorTask task = new ScheduledExecutorTask(runnable);
		task.setPeriod(500);
		task.setFixedRate(true);

		ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean();
		factory.setScheduledExecutorTasks(new ScheduledExecutorTask[]{task});
		factory.afterPropertiesSet();
		pauseToLetTaskStart(2);
		factory.destroy();

		mockRunnable.verify();
	}

	public void testWithInitialDelayRepeatedExecutionIsSetupAndFiresCorrectly() throws Exception {

		MockControl mockRunnable = MockControl.createControl(Runnable.class);
		Runnable runnable = (Runnable) mockRunnable.getMock();
		runnable.run();
		mockRunnable.setVoidCallable();
		runnable.run();
		mockRunnable.setVoidCallable();
		mockRunnable.replay();

		ScheduledExecutorTask task = new ScheduledExecutorTask(runnable);
		task.setPeriod(500);
		task.setDelay(3000); // nice long wait...

		ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean();
		factory.setScheduledExecutorTasks(new ScheduledExecutorTask[]{
			task
		});
		factory.afterPropertiesSet();
		pauseToLetTaskStart(1);
		// invoke destroy before tasks have even been scheduled...
		factory.destroy();

		try {
			mockRunnable.verify();
			fail("Mock must never have been called");
		}
		catch (AssertionFailedError expected) {
		}
	}

	public void testSettingThreadFactoryToNullForcesUseOfDefaultButIsOtherwiseCool() throws Exception {
		ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean() {
			protected ScheduledExecutorService createExecutor(int poolSize, ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
				assertNotNull("Bah; the setThreadFactory(..) method must use a default ThreadFactory if a null arg is passed in.");
				return super.createExecutor(poolSize, threadFactory, rejectedExecutionHandler);
			}
		};
		factory.setScheduledExecutorTasks(new ScheduledExecutorTask[]{
			new ScheduledExecutorFactoryBeanTests.NoOpScheduledExecutorTask()
		});
		factory.setThreadFactory(null); // the null must not propagate
		factory.afterPropertiesSet();
		factory.destroy();
	}

	public void testSettingRejectedExecutionHandlerToNullForcesUseOfDefaultButIsOtherwiseCool() throws Exception {
		ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean() {
			protected ScheduledExecutorService createExecutor(int poolSize, ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
				assertNotNull("Bah; the setRejectedExecutionHandler(..) method must use a default RejectedExecutionHandler if a null arg is passed in.");
				return super.createExecutor(poolSize, threadFactory, rejectedExecutionHandler);
			}
		};
		factory.setScheduledExecutorTasks(new ScheduledExecutorTask[]{
			new ScheduledExecutorFactoryBeanTests.NoOpScheduledExecutorTask()
		});
		factory.setRejectedExecutionHandler(null); // the null must not propagate
		factory.afterPropertiesSet();
		factory.destroy();
	}

	public void testObjectTypeReportsCorrectType() throws Exception {
		ScheduledExecutorFactoryBean factory = new ScheduledExecutorFactoryBean();
		assertEquals(ScheduledExecutorService.class, factory.getObjectType());
	}


	private static void pauseToLetTaskStart(int seconds) {
		try {
			Thread.sleep(seconds * 1000);
		}
		catch (InterruptedException ignored) {
		}
	}


	private static final class NoOpScheduledExecutorTask extends ScheduledExecutorTask {

		public NoOpScheduledExecutorTask() {
			super(new NoOpRunnable());
		}
	}

}
