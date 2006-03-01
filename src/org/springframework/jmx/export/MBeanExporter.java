/*
 * Copyright 2002-2006 the original author or authors.
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

package org.springframework.jmx.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.LazyInitTargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler;
import org.springframework.jmx.export.assembler.MBeanInfoAssembler;
import org.springframework.jmx.export.assembler.SimpleReflectiveMBeanInfoAssembler;
import org.springframework.jmx.export.naming.KeyNamingStrategy;
import org.springframework.jmx.export.naming.ObjectNamingStrategy;
import org.springframework.jmx.export.naming.SelfNaming;
import org.springframework.jmx.export.notification.ModelMBeanNotificationPublisher;
import org.springframework.jmx.export.notification.NotificationPublisherAware;
import org.springframework.jmx.support.JmxUtils;
import org.springframework.jmx.support.MBeanRegistrationSupport;
import org.springframework.util.ObjectUtils;
import org.springframework.core.Constants;

/**
 * A bean that allows for any Spring-managed bean to be exposed to a JMX
 * <code>MBeanServer</code>, without the need to define any JMX-specific
 * information in the bean classes.
 *
 * <p>If the bean implements one of the JMX management interfaces then
 * <code>MBeanExporter</code> can simply register the MBean with the server
 * automatically, through its autodetection process.
 *
 * <p>If the bean does not implement one of the JMX management interfaces then
 * <code>MBeanExporter</code> will create the management information using the
 * supplied <code>MBeanInfoAssembler</code> implementation.
 *
 * <p>A list of <code>MBeanExporterListener</code>s can be registered via the
 * <code>listeners</code> property, allowing application code to be notified
 * of MBean registration and unregistration events.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 1.2
 * @see #setBeans
 * @see #setAutodetect
 * @see #setAssembler
 * @see #setListeners
 * @see org.springframework.jmx.export.assembler.MBeanInfoAssembler
 * @see MBeanExporterListener
 */
public class MBeanExporter extends MBeanRegistrationSupport
		implements InitializingBean, DisposableBean, MBeanExportOperations, BeanFactoryAware {

	/**
	 * Autodetection mode indicating that no autodetection should be used.
	 */
	public static final int AUTODETECT_NONE = 0;

	/**
	 * Autodetection mode indicating that only valid MBeans should be autodetected.
	 */
	public static final int AUTODETECT_MBEAN = 1;

	/**
	 * Autodetection mode indicating that only the {@link MBeanInfoAssembler} should be able
	 * to autodetect beans.
	 */
	public static final int AUTODETECT_ASSEMBLER = 2;

	/**
	 * Autodetection mode indicating that all autodetection mechanisms should be used.
	 */
	public static final int AUTODETECT_ALL = AUTODETECT_MBEAN | AUTODETECT_ASSEMBLER;

	/**
	 * Constant for the JMX <code>mr_type</code> "ObjectReference".
	 */
	private static final String MR_TYPE_OBJECT_REFERENCE = "ObjectReference";

	/**
	 * Wildcard used to map a {@link javax.management.NotificationListener}
	 * to all MBeans registered by the <code>MBeanExporter</code>.
	 */
	private static final String WILDCARD = "*";

	/**
	 * {@link Constants} instance for this class.
	 */
	private static final Constants constants = new Constants(MBeanExporter.class);

	/**
	 * The beans to be exposed as JMX managed resources.
	 */
	private Map beans;

	/**
	 * The autodetect mode to use for this <code>MBeanExporter</code>.
	 * Default value is {@link #AUTODETECT_NONE}.
	 */
	private int autodetectMode = AUTODETECT_NONE;

	/**
	 * Indicates whether Spring should modify generated {@link ObjectName ObjectNames}
	 */
	private boolean ensureUniqueRuntimeObjectNames = true;

	/**
	 * Indicates whether Spring should expose the managed resource {@link ClassLoader} in the MBean.
	 */
	private boolean exposeManagedResourceClassLoader = false;

	/**
	 * A list of bean names that should be excluded from autodetection.
	 */
	private Set excludedBeans;

	/**
	 * The <code>MBeanExporterListeners</code> registered with this exporter.
	 */
	private MBeanExporterListener[] listeners;

	/**
	 * The {@link javax.management.NotificationListener NotificationListeners} to register
	 * for the MBeans registered by this <code>MBeanExporter</code>.
	 */
	private NotificationListenerBean[] notificationListeners = new NotificationListenerBean[0];

	/**
	 * Stores the <code>MBeanInfoAssembler</code> to use for this exporter.
	 */
	private MBeanInfoAssembler assembler = new SimpleReflectiveMBeanInfoAssembler();

	/**
	 * The strategy to use for creating <code>ObjectName</code>s for an object.
	 */
	private ObjectNamingStrategy namingStrategy = new KeyNamingStrategy();

	/**
	 * Stores the <code>BeanFactory</code> for use in autodetection process.
	 */
	private ListableBeanFactory beanFactory;


	/**
	 * Supply a <code>Map</code> of beans to be registered with the JMX
	 * <code>MBeanServer</code>.
	 * <p>The String keys are the basis for the creation of JMX object names.
	 * By default, a JMX <code>ObjectName</code> will be created straight
	 * from the given key. This can be customized through specifying a
	 * custom <code>NamingStrategy</code>.
	 * <p>Both bean instances and bean names are allowed as values.
	 * Bean instances are typically linked in through bean references.
	 * Bean names will be resolved as beans in the current factory, respecting
	 * lazy-init markers (that is, not triggering initialization of such beans).
	 * @param beans Map with bean instances or bean names as values
	 * @see #setNamingStrategy
	 * @see org.springframework.jmx.export.naming.KeyNamingStrategy
	 * @see javax.management.ObjectName#ObjectName(String)
	 */
	public void setBeans(Map beans) {
		this.beans = beans;
	}

	/**
	 * Set whether to autodetect MBeans in the bean factory that this exporter
	 * runs in. Will also ask an <code>AutodetectCapableMBeanInfoAssembler</code>
	 * if available.
	 * <p>This feature is turned off by default. Explicitly specify "true" here
	 * to enable autodetection.
	 * @see #setAssembler
	 * @see AutodetectCapableMBeanInfoAssembler
	 * @see org.springframework.jmx.support.JmxUtils#isMBean
	 * @deprecated in favor of {@link #setAutodetectModeName(String)}
	 */
	public void setAutodetect(boolean autodetect) {
		this.autodetectMode = (autodetect ? AUTODETECT_ALL : AUTODETECT_NONE);
	}

	/**
	 * Sets the autodetection mode to use.
	 * @see #setAutodetectModeName(String)
	 * @see #AUTODETECT_ALL
	 * @see #AUTODETECT_ASSEMBLER
	 * @see #AUTODETECT_MBEAN
	 * @see #AUTODETECT_NONE
	 */
	public void setAutodetectMode(int autodetectMode) {
		this.autodetectMode = autodetectMode;
	}

	/**
	 * Sets the autodetection mode to use by name.
	 * @see #setAutodetectMode(int) 
	 * @see #AUTODETECT_ALL
	 * @see #AUTODETECT_ASSEMBLER
	 * @see #AUTODETECT_MBEAN
	 * @see #AUTODETECT_NONE
	 */
	public void setAutodetectModeName(String autodetectMode) {
		this.autodetectMode = constants.asNumber(autodetectMode).intValue();
	}
	/**
	 * Set the implementation of the <code>MBeanInfoAssembler</code> interface to use
	 * for this exporter. Default is a <code>SimpleReflectiveMBeanInfoAssembler</code>.
	 * <p>The passed-in assembler can optionally implement the
	 * <code>AutodetectCapableMBeanInfoAssembler</code> interface, which enables it
	 * to particiapte in the exporter's MBean autodetection process.
	 * @see org.springframework.jmx.export.assembler.SimpleReflectiveMBeanInfoAssembler
	 * @see org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler
	 * @see org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler
	 * @see #setAutodetect
	 */
	public void setAssembler(MBeanInfoAssembler assembler) {
		this.assembler = assembler;
	}

	/**
	 * Set the implementation of the <code>ObjectNamingStrategy</code> interface
	 * to use for this exporter. Default is a <code>KeyNamingStrategy</code>.
	 * @see org.springframework.jmx.export.naming.KeyNamingStrategy
	 * @see org.springframework.jmx.export.naming.MetadataNamingStrategy
	 */
	public void setNamingStrategy(ObjectNamingStrategy namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	/**
	 * This callback is only required for resolution of bean names in the "beans"
	 * <code>Map</code> and for autodetection of MBeans (in the latter case,
	 * a <code>ListableBeanFactory</code> is required).
	 * @see #setBeans
	 * @see #setAutodetect
	 * @see org.springframework.beans.factory.ListableBeanFactory
	 */
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ListableBeanFactory) {
			this.beanFactory = (ListableBeanFactory) beanFactory;
		}
		else {
			logger.info("Not running in a ListableBeanFactory: autodetection of MBeans not available");
		}
	}

	/**
	 * Set the <code>MBeanExporterListener</code>s that should be notified
	 * of MBean registration and unregistration events.
	 * @see MBeanExporterListener
	 */
	public void setListeners(MBeanExporterListener[] listeners) {
		this.listeners = listeners;
	}

	/**
	 * Set the list of names for beans that should be excluded from autodetection.
	 */
	public void setExcludedBeans(String[] excludedBeans) {
		this.excludedBeans = (excludedBeans != null ? new HashSet(Arrays.asList(excludedBeans)) : null);
	}

	/**
	 * Indicates whether Spring should ensure that {@link ObjectName ObjectNames} generated by
	 * the configured {@link ObjectNamingStrategy} for runtime-registered MBeans should be modified
	 * to ensure uniqueness for every instance of managed <code>Class</code>. Default value is
	 * <code>true</code>.
	 * @see JmxUtils#appendIdentityToObjectName(javax.management.ObjectName, Object)
	 */
	public void setEnsureUniqueRuntimeObjectNames(boolean ensureUniqueRuntimeObjectNames) {
		this.ensureUniqueRuntimeObjectNames = ensureUniqueRuntimeObjectNames;
	}

	/**
	 * Indicates whether or not the managed resource should be exposed as the
	 * {@link Thread#getContextClassLoader() thread context ClassLoader} before allowing
	 * any invocations on the MBean to occur. Default value is <code>false</code>.
	 */
	public void setExposeManagedResourceClassLoader(boolean exposeManagedResourceClassLoader) {
		this.exposeManagedResourceClassLoader = exposeManagedResourceClassLoader;
	}

	/**
	 * Sets the {@link NotificationListenerBean NotificationListenerBeans} containing the
	 * {@link javax.management.NotificationListener NotificationListeners} that will be registered
	 * with the {@link MBeanServer}.
	 * @see #setNotificationListenerMappings(java.util.Map)
	 * @see NotificationListenerBean
	 */
	public void setNotificationListeners(NotificationListenerBean[] notificationListeners) {
		this.notificationListeners = notificationListeners;
	}

	/**
	 * Sets the {@link NotificationListener NotificationListeners} to register with the
	 * {@link javax.management.MBeanServer}. The key of each entry in the <code>Map</code> is
	 * the {@link javax.management.ObjectName} of the MBean the listener should be registered for.
	 * Specifying an asterisk (<code>*</code>) will cause the listener to be associated with all
	 * MBeans registered by this class at startup time.
	 * <p>The value of each entry is the {@link javax.management.NotificationListener} to register.
	 * For more advanced options such as registering
	 * {@link javax.management.NotificationFilter NotificationFilters} and
	 * handback objects see {@link #setNotificationListeners(NotificationListenerBean[])}.
	 * @throws MalformedObjectNameException if one of the supplied
	 * {@link ObjectName ObjectName} instances is malformed
	 * @throws IllegalArgumentException if any of the values in the supplied listeners map
	 * is not a {@link NotificationListener} or is <code>null</code>.
	 */
	public void setNotificationListenerMappings(Map listeners) throws MalformedObjectNameException {
		List notificationListeners = new ArrayList();
		for (Iterator iterator = listeners.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry entry = (Map.Entry) iterator.next();

			// Get the listener from the map value.
			Object value = entry.getValue();
			if (!(value instanceof NotificationListener)) {
				throw new IllegalArgumentException(
						"Map entry value [" + value + "] is not a valid NotificationListener");
			}

			// Get the ObjectName from the map key.
			Object key = entry.getKey();
			ObjectName mappedObjectName = null;

			if (!WILDCARD.equals(key)) {
				mappedObjectName = JmxUtils.convertToObjectName(entry.getKey());
			}

			NotificationListenerBean bean = new NotificationListenerBean((NotificationListener) value);

			if (mappedObjectName != null) {
				// This listener is mapped to a specific ObjectName.
				bean.setMappedObjectName(mappedObjectName);
			}

			notificationListeners.add(bean);
		}
		this.notificationListeners = (NotificationListenerBean[])
				notificationListeners.toArray(new NotificationListenerBean[notificationListeners.size()]);
	}


	//---------------------------------------------------------------------
	// Lifecycle in bean factory: automatically register/unregister beans
	//---------------------------------------------------------------------

	/**
	 * Start bean registration automatically when deployed in an
	 * <code>ApplicationContext</code>.
	 * @see #registerBeans()
	 */
	public void afterPropertiesSet() {
		logger.info("Registering beans for JMX exposure on startup");
		registerBeans();
	}

	/**
	 * Unregisters all beans that this exported has exposed via JMX
	 * when the enclosing <code>ApplicationContext</code> is destroyed.
	 */
	public void destroy() {
		logger.info("Unregistering JMX-exposed beans on shutdown");
		unregisterBeans();
	}


	//---------------------------------------------------------------------
	// Implementation of MBeanExportOperations interface
	//---------------------------------------------------------------------

	public ObjectName registerManagedResource(Object managedResource) throws MBeanExportException {
		try {
			ObjectName objectName = getObjectName(managedResource, null);
			if (this.ensureUniqueRuntimeObjectNames) {
				objectName = JmxUtils.appendIdentityToObjectName(objectName, managedResource);
			}
			registerManagedResource(managedResource, objectName);
			return objectName;
		}
		catch (MalformedObjectNameException ex) {
			throw new MBeanExportException("Unable to generate ObjectName for MBean [" + managedResource + "]", ex);
		}
	}

	public void registerManagedResource(Object managedResource, ObjectName objectName) throws MBeanExportException {
		Object mbean = createAndConfigureMBean(managedResource, managedResource.getClass().getName());
		try {
			doRegister(mbean, objectName);
		}
		catch (JMException ex) {
			throw new UnableToRegisterMBeanException(
					"Unable to register MBean [" + managedResource + "] with object name [" + objectName + "]", ex);
		}
	}


	//---------------------------------------------------------------------
	// Exporter implementation
	//---------------------------------------------------------------------

	/**
	 * Registers the defined beans with the <code>MBeanServer</code>. Each bean is exposed
	 * to the <code>MBeanServer</code> via a <code>ModelMBean</code>. The actual implemetation
	 * of the <code>ModelMBean</code> interface used depends on the implementation of the
	 * <code>ModelMBeanProvider</code> interface that is configured. By default the
	 * <code>RequiredModelMBean</code> class that is supplied with all JMX implementations
	 * is used.
	 * <p>The management interface produced for each bean is dependent on the
	 * <code>MBeanInfoAssembler</code> implementation being used.
	 * The <code>ObjectName</code> given to each bean is dependent on the implementation
	 * of the <code>ObjectNamingStrategy</code> interface being used.
	 */
	protected void registerBeans() {
		// If no server was provided then try to find one.
		// This is useful in an environment such as JDK 1.5, Tomcat
		// or JBoss where there is already an MBeanServer loaded.
		if (this.server == null) {
			this.server = JmxUtils.locateMBeanServer();
		}

		// The beans property may be <code>null</code>, for example
		// if we are relying solely on autodetection.
		if (this.beans == null) {
			this.beans = new HashMap();
		}

		// Perform autodetection, if desired.
		if (this.autodetectMode != AUTODETECT_NONE) {
			if (this.beanFactory == null) {
				throw new MBeanExportException("Cannot autodetect MBeans if not running in a BeanFactory");
			}

			if (isAutodetectModeEnabled(AUTODETECT_MBEAN)) {
				// Autodetect any beans that are already MBeans.
				logger.info("Autodetecting user-defined JMX MBeans");
				autodetectMBeans();
			}

			// Allow the assembler a chance to vote for bean inclusion.
			if (isAutodetectModeEnabled(AUTODETECT_ASSEMBLER)
							&& this.assembler instanceof AutodetectCapableMBeanInfoAssembler) {
				autodetectBeans((AutodetectCapableMBeanInfoAssembler) this.assembler);
			}
		}

		// Check we now have at least one bean.
		if (this.beans.isEmpty()) {
			throw new IllegalArgumentException("Must specify at least one bean for registration");
		}

		try {
			for (Iterator it = this.beans.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();
				String beanKey = (String) entry.getKey();
				Object value = entry.getValue();
				registerBeanNameOrInstance(value, beanKey);
			}

			// all MBeans are now registered successfully - go ahead and register the notification listeners
			registerNotificationListeners();
		}
		catch (MBeanExportException ex) {
			// Unregister beans already registered by this exporter.
			unregisterBeans();
			throw ex;
		}
	}

	/**
	 * Return whether the specified bean definition should be considered as lazy-init.
	 * @param beanFactory the bean factory that is supposed to contain the bean definition
	 * @param beanName the name of the bean to check
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 * @see org.springframework.beans.factory.config.BeanDefinition#isLazyInit
	 */
	protected boolean isBeanDefinitionLazyInit(ListableBeanFactory beanFactory, String beanName) {
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			return false;
		}
		try {
			BeanDefinition bd = ((ConfigurableListableBeanFactory) beanFactory).getBeanDefinition(beanName);
			return bd.isLazyInit();
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Probably a directly registered singleton.
			return false;
		}
	}

	/**
	 * Registers an individual bean with the <code>MBeanServer</code>. This method
	 * is responsible for deciding <strong>how</strong> a bean should be exposed
	 * to the <code>MBeanServer</code>. Specifically, if the <code>mapValue</code>
	 * is the name of a bean that is configured for lazy initialization, then
	 * a proxy to the resource is registered with the <code>MBeanServer</code>
	 * so that the the lazy load behavior is honored. If the bean is already an
	 * MBean then it will be registered directly with the <code>MBeanServer</code>
	 * without any intervention. For all other beans or bean names, the resource
	 * itself is registered with the <code>MBeanServer</code> directly.
	 * @param beanKey the key associated with this bean in the beans map
	 * @param mapValue the value configured for this bean in the beans map.
	 * May be either the <code>String</code> name of a bean, or the bean itself.
	 * @return the <code>ObjectName</code> under which the resource was registered
	 * @throws MBeanExportException if the export failed
	 * @see #setBeans
	 * @see #registerLazyInit
	 * @see #registerMBean
	 * @see #registerSimpleBean
	 */
	protected ObjectName registerBeanNameOrInstance(Object mapValue, String beanKey) throws MBeanExportException {
		try {
			if (mapValue instanceof String) {
				// Bean name pointing to a potentially lazy-init bean in the factory.
				if (this.beanFactory == null) {
					throw new MBeanExportException("Cannot resolve bean names if not running in a BeanFactory");
				}
				String beanName = (String) mapValue;
				if (isBeanDefinitionLazyInit(this.beanFactory, beanName)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Found bean name for lazy init bean with key [" + beanKey +
								"]. Registering bean with lazy init support.");
					}
					return registerLazyInit(beanName, beanKey);
				}
				else {
					if (logger.isDebugEnabled()) {
						logger.debug("String value under key [" + beanKey + "] points to a bean that was not " +
								"registered for lazy initialization. Registering bean normally with JMX server.");
					}
					Object bean = this.beanFactory.getBean(beanName);
					return registerBeanInstance(bean, beanKey);
				}
			}
			else {
				// Plain bean instance -> register it directly.
				return registerBeanInstance(mapValue, beanKey);
			}
		}
		catch (JMException ex) {
			throw new UnableToRegisterMBeanException(
					"Unable to register MBean [" + mapValue + "] with key [" + beanKey + "]", ex);
		}
	}

	/**
	 * Registers an existing MBean or an MBean adapter for a plain bean
	 * with the <code>MBeanServer</code>.
	 * @param bean the bean to register, either an MBean or a plain bean
	 * @param beanKey the key associated with this bean in the beans map
	 * @return the <code>ObjectName</code> under which the bean was registered
	 * with the <code>MBeanServer</code>
	 */
	private ObjectName registerBeanInstance(Object bean, String beanKey) throws JMException {
		if (JmxUtils.isMBean(bean.getClass())) {
			if (logger.isDebugEnabled()) {
				logger.debug("Located MBean under key [" + beanKey + "]: registering with JMX server");
			}
			return registerMBean(bean, beanKey);
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("Located simple bean under key [" + beanKey + "]: registering with JMX server");
			}
			return registerSimpleBean(bean, beanKey);
		}
	}

	/**
	 * Registers an existing MBean with the <code>MBeanServer</code>.
	 * @param mbean the bean instance to register
	 * @param beanKey the key associated with this bean in the beans map
	 * @return the <code>ObjectName</code> under which the bean was registered
	 * with the <code>MBeanServer</code>
	 */
	private ObjectName registerMBean(Object mbean, String beanKey) throws JMException {
		ObjectName objectName = getObjectName(mbean, beanKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Registering MBean [" + objectName + "]");
		}
		doRegister(mbean, objectName);
		return objectName;
	}

	/**
	 * Registers a plain bean as MBean with the <code>MBeanServer</code>.
	 * The management interface for the bean is created by the configured
	 * <code>MBeanInfoAssembler</code>.
	 * @param bean the bean instance to register
	 * @param beanKey the key associated with this bean in the beans map
	 * @return the <code>ObjectName</code> under which the bean was registered
	 * with the <code>MBeanServer</code>
	 */
	private ObjectName registerSimpleBean(Object bean, String beanKey) throws JMException {
		ObjectName objectName = getObjectName(bean, beanKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Registering and assembling MBean [" + objectName + "]");
		}
		ModelMBean mbean = createAndConfigureMBean(bean, beanKey);
		doRegister(mbean, objectName);
		injectNotificationPublisherIfNecessary(bean, mbean);
		return objectName;
	}

	/**
	 * Registers beans that are configured for lazy initialization with the
	 * <code>MBeanServer<code> indirectly through a proxy.
	 * @param beanName the name of the bean in the <code>BeanFactory</code>
	 * @param beanKey the key associated with this bean in the beans map
	 * @return the <code>ObjectName</code> under which the bean was registered
	 * with the <code>MBeanServer</code>
	 */
	private ObjectName registerLazyInit(String beanName, String beanKey) throws JMException {
		NotificationPublisherAwareLazyTargetSource targetSource = new NotificationPublisherAwareLazyTargetSource();
		targetSource.setTargetBeanName(beanName);
		targetSource.setBeanFactory(this.beanFactory);

		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.setFrozen(true);

		Object proxy = proxyFactory.getProxy();
		ObjectName objectName = getObjectName(proxy, beanKey);
		if (logger.isDebugEnabled()) {
			logger.debug("Registering lazy-init MBean [" + objectName + "]");
		}

		ModelMBean mbean = createAndConfigureMBean(proxy, beanKey);
		targetSource.setModelMBean(mbean);

		doRegister(mbean, objectName);
		return objectName;
	}

	/**
	 * Retrieve the <code>ObjectName</code> for a bean.
	 * <p>If the bean implements the <code>SelfNaming</code> interface, then the
	 * <code>ObjectName</code> will be retrieved using <code>SelfNaming.getObjectName()</code>.
	 * Otherwise, the configured <code>ObjectNamingStrategy</code> is used.
	 * @param bean the name of the bean in the <code>BeanFactory</code>
	 * @param beanKey the key associated with the bean in the beans map
	 * @return the <code>ObjectName</code> for the supplied bean
	 * @throws javax.management.MalformedObjectNameException
	 * if the retrieved <code>ObjectName</code> is malformed
	 */
	protected ObjectName getObjectName(Object bean, String beanKey) throws MalformedObjectNameException {
		if (bean instanceof SelfNaming) {
			return ((SelfNaming) bean).getObjectName();
		}
		else {
			return this.namingStrategy.getObjectName(bean, beanKey);
		}
	}

	/**
	 * Creates an MBean that is configured with the appropriate management interface
	 * for the supplied managed resource.
	 */
	protected ModelMBean createAndConfigureMBean(Object managedResource, String beanKey)
			throws MBeanExportException {
		try {
			ModelMBean mbean = createModelMBean();
			mbean.setModelMBeanInfo(getMBeanInfo(managedResource, beanKey));
			mbean.setManagedResource(managedResource, MR_TYPE_OBJECT_REFERENCE);
			return mbean;
		}
		catch (Exception ex) {
			throw new MBeanExportException("Could not create ModelMBean for managed resource [" +
					managedResource + "] with key [" + beanKey + "]", ex);
		}
	}

	/**
	 * Create an instance of a class that implements <code>ModelMBean</code>.
	 * <p>This method is called to obtain a <code>ModelMBean</code> instance to
	 * use when registering a bean. This method is called once per bean during the
	 * registration phase and must return a new instance of <code>ModelMBean</code>
	 * @return a new instance of a class that implements <code>ModelMBean</code>
	 * @throws javax.management.MBeanException if creation of the ModelMBean failed
	 */
	protected ModelMBean createModelMBean() throws MBeanException {
		return (this.exposeManagedResourceClassLoader ? new SpringModelMBean() : new RequiredModelMBean());
	}

	/**
	 * Gets the <code>ModelMBeanInfo</code> for the bean with the supplied key
	 * and of the supplied type.
	 */
	private ModelMBeanInfo getMBeanInfo(Object managedBean, String beanKey) throws JMException {
		ModelMBeanInfo info = this.assembler.getMBeanInfo(managedBean, beanKey);
		if (logger.isWarnEnabled() && ObjectUtils.isEmpty(info.getAttributes()) &&
				ObjectUtils.isEmpty(info.getOperations())) {
			logger.warn("Bean with key [" + beanKey +
					"] has been registed as an MBean but has no exposed attributes or operations");
		}
		return info;
	}


	//---------------------------------------------------------------------
	// Autodetection process
	//---------------------------------------------------------------------

	/**
	 * Returns <code>true</code> if the particular autodetect mode is enabled
	 * otherwise returns <code>false</code>.
	 */
	private boolean isAutodetectModeEnabled(int mode) {
		return (this.autodetectMode & mode) == mode;
	}

	/**
	 * Invoked when using an <code>AutodetectCapableMBeanInfoAssembler</code>.
	 * Gives the assembler the opportunity to add additional beans from the
	 * <code>BeanFactory</code> to the list of beans to be exposed via JMX.
	 * <p>This implementation prevents a bean from being added to the list
	 * automatically if it has already been added manually, and it prevents
	 * certain internal classes from being registered automatically.
	 */
	private void autodetectBeans(final AutodetectCapableMBeanInfoAssembler assembler) {
		autodetect(new AutodetectCallback() {
			public boolean include(Class beanClass, String beanName) {
				return assembler.includeBean(beanClass, beanName);
			}
		});
	}

	/**
	 * Attempts to detect any beans defined in the <code>ApplicationContext</code> that are
	 * valid MBeans and registers them automatically with the <code>MBeanServer</code>.
	 */
	private void autodetectMBeans() {
		autodetect(new AutodetectCallback() {
			public boolean include(Class beanClass, String beanName) {
				return JmxUtils.isMBean(beanClass);
			}
		});
	}

	/**
	 * Performs the actual autodetection process, delegating to an instance
	 * <code>AutodetectCallback</code> to vote on the inclusion of a given bean.
	 * @param callback the <code>AutodetectCallback</code> to use when deciding
	 * whether to include a bean or not
	 */
	private void autodetect(AutodetectCallback callback) {
		String[] beanNames = this.beanFactory.getBeanNamesForType(null);
		for (int i = 0; i < beanNames.length; i++) {
			String beanName = beanNames[i];

			if (!isExcluded(beanName)) {
				Class beanClass = this.beanFactory.getType(beanName);
				if (beanClass != null && callback.include(beanClass, beanName)) {
					boolean lazyInit = isBeanDefinitionLazyInit(this.beanFactory, beanName);
					Object beanInstance = (!lazyInit ? this.beanFactory.getBean(beanName) : null);
					if (!this.beans.containsValue(beanName) &&
							(beanInstance == null || !this.beans.containsValue(beanInstance))) {
						// Not already registered for JMX exposure.
						this.beans.put(beanName, (beanInstance != null ? beanInstance : beanName));
						if (logger.isInfoEnabled()) {
							logger.info("Bean with name '" + beanName + "' has been autodetected for JMX exposure");
						}
					}
					else {
						if (logger.isDebugEnabled()) {
							logger.debug("Bean with name '" + beanName + "' is already registered for JMX exposure");
						}
					}
				}
			}
		}
	}

	/**
	 * Indicates whether or not a particular bean name is present in the excluded beans list.
	 */
	private boolean isExcluded(String beanName) {
		return (this.excludedBeans != null && this.excludedBeans.contains(beanName));
	}


	//---------------------------------------------------------------------
	// Management of notification listeners
	//---------------------------------------------------------------------

	/**
	 * If the supplied managed resource implements the {@link NotificationPublisherAware} an instance of
	 * {@link org.springframework.jmx.export.notification.NotificationPublisher} is injected.
	 */
	private void injectNotificationPublisherIfNecessary(Object managedResource, ModelMBean modelMBean) {
		if (managedResource instanceof NotificationPublisherAware) {
			((NotificationPublisherAware) managedResource).setNotificationPublisher(
					new ModelMBeanNotificationPublisher(modelMBean));
		}
	}

	/**
	 * Registers the configured {@link NotificationListener NotificationListeners}
	 * with the {@link MBeanServer}.
	 */
	private void registerNotificationListeners() {
		for (int i = 0; i < this.notificationListeners.length; i++) {
			NotificationListenerBean bean = this.notificationListeners[i];
			NotificationListener listener = bean.getNotificationListener();
			NotificationFilter filter = bean.getNotificationFilter();
			Object handback = bean.getHandback();
			ObjectName[] namesToRegisterWith = getObjectNamesForNotificationListener(bean);
			for (int j = 0; j < namesToRegisterWith.length; j++) {
				ObjectName objectName = namesToRegisterWith[j];
				try {
					this.server.addNotificationListener(objectName, listener, filter, handback);
				}
				catch (InstanceNotFoundException ex) {
					throw new MBeanExportException("Unable to register NotificationListener for MBean [" +
							objectName + "] because that MBean instance does not exist", ex);
				}
			}
		}
	}

	/**
	 * Retrieves the {@link javax.management.ObjectName ObjectNames} for which a
	 * {@link NotificationListener} should be registered.
	 */
	private ObjectName[] getObjectNamesForNotificationListener(NotificationListenerBean bean) {
		ObjectName[] mappedObjectNames = bean.getMappedObjectNames();
		if (mappedObjectNames == null) {
			// Mapped to all MBeans registered by the MBeanExporter.
			return (ObjectName[]) this.registeredBeans.toArray(new ObjectName[this.registeredBeans.size()]);
		}
		else {
			return mappedObjectNames;
		}
	}

	/**
	 * Called when an MBean is registered. Notifies all registered
	 * {@link MBeanExporterListener MBeanExporterListeners} of the registration event.
	 */
	protected void onRegister(ObjectName objectName) {
		notifyListenersOfRegistration(objectName);
	}

	/**
	 * Called when an MBean is registered. Notifies all registered
	 * {@link MBeanExporterListener MBeanExporterListeners} of the unregistration event.
	 */
	protected void onUnregister(ObjectName objectName) {
		notifyListenersOfUnregistration(objectName);
	}

	/**
	 * Notifies all registered {@link MBeanExporterListener MBeanExporterListeners} of the
	 * registration of the MBean identified by the supplied {@link ObjectName}.
	 */
	private void notifyListenersOfRegistration(ObjectName objectName) {
		if (this.listeners != null) {
			for (int i = 0; i < this.listeners.length; i++) {
				this.listeners[i].mbeanRegistered(objectName);
			}
		}
	}

	/**
	 * Notifies all registered {@link MBeanExporterListener MBeanExporterListeners} of the
	 * unregistration of the MBean identified by the supplied {@link ObjectName}.
	 */
	private void notifyListenersOfUnregistration(ObjectName objectName) {
		if (this.listeners != null) {
			for (int i = 0; i < this.listeners.length; i++) {
				this.listeners[i].mbeanUnregistered(objectName);
			}
		}
	}


	//---------------------------------------------------------------------
	// Inner classes for internal use
	//---------------------------------------------------------------------

	/**
	 * Internal callback interface for the autodetection process.
	 */
	private static interface AutodetectCallback {

		/**
		 * Called during the autodetection process to decide whether
		 * or not a bean should be included.
		 * @param beanClass the class of the bean
		 * @param beanName the name of the bean
		 */
		boolean include(Class beanClass, String beanName);
	}


	/**
	 * Extension of {@link LazyInitTargetSource} that will inject a
	 * {@link org.springframework.jmx.export.notification.NotificationPublisher}
	 * into the lazy resource as it is created if required.
	 */
	private class NotificationPublisherAwareLazyTargetSource extends LazyInitTargetSource {

		private ModelMBean modelMBean;

		public void setModelMBean(ModelMBean modelMBean) {
			this.modelMBean = modelMBean;
		}

		protected void postProcessTargetObject(Object targetObject) {
			injectNotificationPublisherIfNecessary(targetObject, this.modelMBean);
		}
	}

}
