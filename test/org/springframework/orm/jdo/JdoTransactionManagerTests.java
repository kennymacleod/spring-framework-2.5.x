package org.springframework.orm.jdo;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import javax.sql.DataSource;

import junit.framework.TestCase;
import org.easymock.MockControl;

import org.springframework.transaction.InvalidIsolationException;
import org.springframework.transaction.InvalidTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * @author Juergen Hoeller
 */
public class JdoTransactionManagerTests extends TestCase {

	public void testTransactionCommit() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 3);
		pm.close();
		pmControl.setVoidCallable(1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		tx.commit();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		final List l = new ArrayList();
		l.add("test");
		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());

		try {
			Object result = tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					assertTrue("Has thread pm", PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
					JdoTemplate jt = new JdoTemplate(pmf);
					return jt.execute(new JdoCallback() {
						public Object doInJdo(PersistenceManager pm) {
							return l;
						}
					});
				}
			});
			assertTrue("Correct result list", result == l);
		}
		catch (RuntimeException ex) {
			fail("Should not have thrown RuntimeException");
		}

		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());
		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testTransactionRollback() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 3);
		pm.close();
		pmControl.setVoidCallable(1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		tx.rollback();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());

		try {
			tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					assertTrue("Has thread pm", PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
					JdoTemplate jt = new JdoTemplate(pmf);
					return jt.execute(new JdoCallback() {
						public Object doInJdo(PersistenceManager pm) {
							throw new RuntimeException("application exception");
						}
					});
				}
			});
			fail("Should have thrown RuntimeException");
		}
		catch (RuntimeException ex) {
			// expected
		}

		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());
		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testTransactionRollbackOnly() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 3);
		pm.close();
		pmControl.setVoidCallable(1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		tx.rollback();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));

		try {
			tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					assertTrue("Has thread pm", PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
					JdoTemplate jt = new JdoTemplate(pmf);
					jt.execute(new JdoCallback() {
						public Object doInJdo(PersistenceManager pm) {
							return null;
						}
					});
					status.setRollbackOnly();
					return null;
				}
			});
		}
		catch (RuntimeException ex) {
			fail("Should not have thrown RuntimeException");
		}

		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testNestedTransactionCommit() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		final MockControl txControl = MockControl.createControl(Transaction.class);
		final Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 4);
		pm.close();
		pmControl.setVoidCallable(1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		final TransactionTemplate tt = new TransactionTemplate(tm);
		final List l = new ArrayList();
		l.add("test");

		try {
			Object result = tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					txControl.reset();
					tx.isActive();
					txControl.setReturnValue(true, 1);
					tx.commit();
					txControl.setVoidCallable(1);
					txControl.replay();

					return tt.execute(new TransactionCallback() {
						public Object doInTransaction(TransactionStatus status) {
							JdoTemplate jt = new JdoTemplate(pmf);
							return jt.execute(new JdoCallback() {
								public Object doInJdo(PersistenceManager pm) {
									return l;
								}
							});
						}
					});
				}
			});
			assertTrue("Correct result list", result == l);
		}
		catch (RuntimeException ex) {
			fail("Should not have thrown RuntimeException");
		}

		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testNestedTransactionRollback() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		final MockControl txControl = MockControl.createControl(Transaction.class);
		final Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 4);
		pm.close();
		pmControl.setVoidCallable(1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		final TransactionTemplate tt = new TransactionTemplate(tm);
		try {
			tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					txControl.reset();
					tx.isActive();
					txControl.setReturnValue(true, 1);
					tx.rollback();
					txControl.setVoidCallable(1);
					txControl.replay();

					return tt.execute(new TransactionCallback() {
						public Object doInTransaction(TransactionStatus status) {
							JdoTemplate jt = new JdoTemplate(pmf);
							return jt.execute(new JdoCallback() {
								public Object doInJdo(PersistenceManager pm) {
									throw new RuntimeException("application exception");
								}
							});
						}
					});
				}
			});
			fail("Should not thrown RuntimeException");
		}
		catch (RuntimeException ex) {
			// expected
		}
		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testNestedTransactionRollbackOnly() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		final MockControl txControl = MockControl.createControl(Transaction.class);
		final Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 4);
		pm.close();
		pmControl.setVoidCallable(1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		final TransactionTemplate tt = new TransactionTemplate(tm);
		final List l = new ArrayList();
		l.add("test");

		try {
			tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					txControl.reset();
					tx.isActive();
					txControl.setReturnValue(true, 1);
					tx.rollback();
					txControl.setVoidCallable(1);
					txControl.replay();

					return tt.execute(new TransactionCallback() {
						public Object doInTransaction(TransactionStatus status) {
							JdoTemplate jt = new JdoTemplate(pmf);
							jt.execute(new JdoCallback() {
								public Object doInJdo(PersistenceManager pm) {
									return l;
								}
							});
							status.setRollbackOnly();
							return null;
						}
					});
				}
			});
		}
		catch (RuntimeException ex) {
			fail("Should not have thrown RuntimeException");
		}

		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testInvalidIsolation() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
		try {
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
				}
			});
			fail("Should have thrown InvalidIsolationException");
		}
		catch (InvalidIsolationException ex) {
			// expected
		}

		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testInvalidTimeout() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		tt.setTimeout(10);
		try {
			tt.execute(new TransactionCallbackWithoutResult() {
				protected void doInTransactionWithoutResult(TransactionStatus status) {
				}
			});
			fail("Should have thrown InvalidTimeoutException");
		}
		catch (InvalidTimeoutException ex) {
			// expected
		}

		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testTransactionCommitWithPrebound() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 3);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		tx.commit();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		pmControl.replay();
		txControl.replay();

		PlatformTransactionManager tm = new JdoTransactionManager(pmf);
		TransactionTemplate tt = new TransactionTemplate(tm);
		final List l = new ArrayList();
		l.add("test");
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());
		PersistenceManagerFactoryUtils.getThreadObjectManager().bindThreadObject(pmf, new PersistenceManagerHolder(pm));
		assertTrue("Has thread pm", PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));

		try {
			Object result = tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					assertTrue("Has thread pm", PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
					JdoTemplate jt = new JdoTemplate(pmf);
					return jt.execute(new JdoCallback() {
						public Object doInJdo(PersistenceManager pm) {
							return l;
						}
					});
				}
			});
			assertTrue("Correct result list", result == l);
		}
		catch (RuntimeException ex) {
			fail("Should not have thrown RuntimeException");
		}

		assertTrue("Has thread pm", PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		PersistenceManagerFactoryUtils.getThreadObjectManager().removeThreadObject(pmf);
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());
		pmfControl.verify();
		pmControl.verify();
		txControl.verify();
	}

	public void testTransactionCommitWithDataSource() {
		MockControl pmfControl = MockControl.createControl(PersistenceManagerFactory.class);
		final PersistenceManagerFactory pmf = (PersistenceManagerFactory) pmfControl.getMock();
		MockControl dsControl = MockControl.createControl(DataSource.class);
		final DataSource ds = (DataSource) dsControl.getMock();
		MockControl dialectControl = MockControl.createControl(JdoDialect.class);
		JdoDialect dialect = (JdoDialect) dialectControl.getMock();
		MockControl pmControl = MockControl.createControl(PersistenceManager.class);
		final PersistenceManager pm = (PersistenceManager) pmControl.getMock();
		MockControl txControl = MockControl.createControl(Transaction.class);
		Transaction tx = (Transaction) txControl.getMock();
		MockControl conControl = MockControl.createControl(Connection.class);
		final Connection con = (Connection) conControl.getMock();
		pmf.getPersistenceManager();
		pmfControl.setReturnValue(pm, 1);
		pm.currentTransaction();
		pmControl.setReturnValue(tx, 3);
		pm.close();
		pmControl.setVoidCallable(1);
		tx.isActive();
		txControl.setReturnValue(false, 1);
		tx.begin();
		txControl.setVoidCallable(1);
		dialect.getJdbcConnection(pm);
		dialectControl.setReturnValue(con);
		tx.commit();
		txControl.setVoidCallable(1);
		pmfControl.replay();
		dsControl.replay();
		dialectControl.replay();
		pmControl.replay();
		txControl.replay();
		conControl.replay();

		JdoTransactionManager tm = new JdoTransactionManager(pmf);
		tm.setDataSource(ds);
		tm.setJdoDialect(dialect);
		TransactionTemplate tt = new TransactionTemplate(tm);
		final List l = new ArrayList();
		l.add("test");
		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());

		try {
			Object result = tt.execute(new TransactionCallback() {
				public Object doInTransaction(TransactionStatus status) {
					assertTrue("Has thread pm", PersistenceManagerFactoryUtils.isPersistenceManagerBoundToThread(pm, pmf));
					assertTrue("Has thread con", DataSourceUtils.isConnectionBoundToThread(con, ds));
					JdoTemplate jt = new JdoTemplate(pmf);
					return jt.execute(new JdoCallback() {
						public Object doInJdo(PersistenceManager pm) {
							return l;
						}
					});
				}
			});
			assertTrue("Correct result list", result == l);
		}
		catch (RuntimeException ex) {
			fail("Should not have thrown RuntimeException");
		}

		assertTrue("Hasn't thread pm", !PersistenceManagerFactoryUtils.getThreadObjectManager().hasThreadObject(pmf));
		assertTrue("Hasn't thread con", !DataSourceUtils.getThreadObjectManager().hasThreadObject(ds));
		assertTrue("JTA synchronizations not active", !TransactionSynchronizationManager.isActive());
		pmfControl.verify();
		dsControl.verify();
		dialectControl.verify();
		pmControl.verify();
		txControl.verify();
		conControl.verify();
	}

}
