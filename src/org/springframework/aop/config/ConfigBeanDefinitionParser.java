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

package org.springframework.aop.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.aspectj.AspectJAfterAdvice;
import org.springframework.aop.aspectj.AspectJAfterReturningAdvice;
import org.springframework.aop.aspectj.AspectJAfterThrowingAdvice;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJMethodBeforeAdvice;
import org.springframework.aop.aspectj.AspectJPointcutAdvisor;
import org.springframework.aop.aspectj.DeclareParentsAdvisor;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.PrioritizedParameterNameDiscoverer;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link BeanDefinitionParser} for the <code>&lt;aop:config&gt;</code> tag.
 * @author Rob Harrop
 * @author Adrian Colyer
 * @author Rod Johnson
 * @since 2.0
 */
class ConfigBeanDefinitionParser implements BeanDefinitionParser {

	public static final String ASPECT = "aspect";

	public static final String EXPRESSION = "expression";

	public static final String ID = "id";

	public static final String POINTCUT = "pointcut";

	public static final String ADVICE = "advice";

	public static final String ADVISOR = "advisor";

	public static final String ADVICE_REF = "advice-ref";

	public static final String POINTCUT_REF = "pointcut-ref";

	public static final String REF = "ref";

	public static final String KIND = "kind";

	public static final String BEFORE = "before";

	public static final String DECLARE_PARENTS = "declare-parents";

	public static final String TYPE_PATTERN = "types-matching";

	public static final String DEFAULT_IMPL = "default-impl";

	public static final String IMPLEMENT_INTERFACE = "implement-interface";

	public static final String AFTER = "after";

	public static final String AFTER_RETURNING = "afterReturning";

	public static final String AFTER_THROWING = "afterThrowing";

	public static final String AFTER_RETURNING_ELEMENT = "after-returning";

	public static final String AFTER_THROWING_ELEMENT = "after-throwing";

	public static final String AROUND = "around";

	public static final String PROXY_TARGET_CLASS = "proxy-target-class";

	public static final String TRUE = "true";

	public static final String RETURNING = "returning";

	public static final String RETURNING_PROPERTY = "returningName";

	public static final String THROWING = "throwing";

	public static final String THROWING_PROPERTY = "throwingName";

	public static final String ARG_NAMES = "arg-names";

	public static final String ARG_NAMES_PROPERTY = "argumentNames";

	public static final String ASPECT_NAME_PROPERTY = "aspectName";

	public static final String ASPECT_BEAN_PROPERTY = "aspectBean";

	public static final String DECLARATION_ORDER_PROPERTY = "declarationOrder";

	public static final String ORDER_PROPERTY = "order";

	private static final int METHOD_INDEX = 0;

	private static final int POINTCUT_INDEX = 1;

	private static final int ASPECT_INSTANCE_FACTORY_INDEX = 2;

	private static final int PARAMETER_NAME_DISCOVERER = 3;


	private final Log logger = LogFactory.getLog(getClass());


	public BeanDefinition parse(Element element, ParserContext parserContext) {
		configureAutoProxyCreator(parserContext, element);

		BeanDefinitionRegistry registry = parserContext.getRegistry();
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				String localName = node.getLocalName();
				if (POINTCUT.equals(localName)) {
					parsePointcut((Element) node, parserContext);
				}
				else if (ADVISOR.equals(localName)) {
					parseAdvisor((Element) node, parserContext);
				}
				else if (ASPECT.equals(localName)) {
					parseAspect((Element) node, parserContext);
				}
			}
		}

		return null;
	}

	/**
	 * Configures the auto proxy creator needed to support the {@link BeanDefinition BeanDefinitions}
	 * created by the '<code>&lt;aop:config/&gt;</code>' tag. Will force class proxying if the
	 * '<code>proxy-target-class</code>' attribute is set to '<code>true</code>'.
	 * @see NamespaceHandlerUtils
	 */
	private void configureAutoProxyCreator(ParserContext parserContext, Element element) {
		NamespaceHandlerUtils.registerAspectJAutoProxyCreatorIfNecessary(parserContext);

		boolean proxyTargetClass = TRUE.equals(element.getAttribute(PROXY_TARGET_CLASS));
		if (proxyTargetClass) {
			NamespaceHandlerUtils.forceAutoProxyCreatorToUseClassProxying(parserContext.getRegistry());
		}
	}

	/**
	 * Parses the supplied <code>&lt;advisor&gt;</code> element and registers the resulting
	 * {@link org.springframework.aop.Advisor} and any resultant {@link org.springframework.aop.Pointcut}
	 * with the supplied {@link BeanDefinitionRegistry}.
	 */
	private void parseAdvisor(Element advisorElement, ParserContext parserContext) {
		BeanDefinitionRegistry registry = parserContext.getRegistry();

		AbstractBeanDefinition advisorDefinition = createAdvisorBeanDefinition(advisorElement);
		String pointcutBeanName = parsePointcutProperty(advisorElement, advisorDefinition.getPropertyValues(), registry);

		String advisorBeanName = advisorElement.getAttribute(ID);

		if (!StringUtils.hasText(advisorBeanName)) {
			advisorBeanName = BeanDefinitionReaderUtils.generateBeanName(advisorDefinition, registry, false);
		}

		registry.registerBeanDefinition(advisorBeanName, advisorDefinition);

		boolean pointcutRef = advisorElement.hasAttribute(POINTCUT_REF);
		fireAdvisorEvent(advisorBeanName, pointcutBeanName, advisorDefinition, parserContext, pointcutRef);
	}

	/**
	 * Creates the {@link AdvisorComponentDefinition} appropriate to the supplied {@link RootBeanDefinition advisor definition}
	 * and fires it through the {@link ParserContext}.
	 */
	private void fireAdvisorEvent(String advisorBeanName, String pointcutBeanName, AbstractBeanDefinition advisorDefinition,
																ParserContext parserContext, boolean pointcutRef) {
		AdvisorComponentDefinition componentDefinition;
		if (pointcutRef) {
			componentDefinition = new AdvisorComponentDefinition(advisorBeanName, advisorDefinition);
		}
		else {
			BeanDefinition pointcutDefinition = parserContext.getRegistry().getBeanDefinition(pointcutBeanName);
			componentDefinition = new AdvisorComponentDefinition(advisorBeanName, advisorDefinition, pointcutDefinition);
		}
		parserContext.getReaderContext().fireComponentRegistered(componentDefinition);
	}

	/**
	 * Creates a {@link RootBeanDefinition} for the advisor described in the supplied. Does <strong>not</strong>
	 * parse any associated '<code>pointcut</code>' or '<code>pointcut-ref</code>' attributes.
	 */
	private AbstractBeanDefinition createAdvisorBeanDefinition(Element advisorElement) {
		RootBeanDefinition advisorDefinition = new RootBeanDefinition(AspectJPointcutAdvisor.class);
		advisorDefinition.setSource(advisorElement);

		MutablePropertyValues mpvs = advisorDefinition.getPropertyValues();
		if (advisorElement.hasAttribute(ORDER_PROPERTY)) {
			mpvs.addPropertyValue(ORDER_PROPERTY, advisorElement.getAttribute(ORDER_PROPERTY));
		}
		advisorDefinition.setPropertyValues(mpvs);

		mpvs.addPropertyValue(ADVICE, new RuntimeBeanReference(advisorElement.getAttribute(ADVICE_REF)));
		return advisorDefinition;
	}

	private void parseAspect(Element aspectElement, ParserContext parserContext) {
		BeanDefinitionRegistry registry = parserContext.getRegistry();
		String aspectName = aspectElement.getAttribute(REF);
		String aspectId = aspectElement.getAttribute(ID);

		List beanDefinitions = new ArrayList();
		List beanReferences = new ArrayList();
		beanReferences.add(new RuntimeBeanReference(aspectName));

		List pointcuts = DomUtils.getChildElementsByTagName(aspectElement, POINTCUT);
		for (int i = 0; i < pointcuts.size(); i++) {
			Element pointcutElement = (Element) pointcuts.get(i);
			beanDefinitions.add(parsePointcut(pointcutElement, parserContext));
		}

		List declareParents = DomUtils.getChildElementsByTagName(aspectElement, DECLARE_PARENTS);
		for (int i = METHOD_INDEX; i < declareParents.size(); i++) {
			Element declareParentsElement = (Element) declareParents.get(i);
			beanDefinitions.add(parseDeclareParents(aspectName, declareParentsElement, parserContext));
		}

		// we have to parse "advice" and all the advice kinds in one loop, to get the
		// ordering semantics right
		NodeList nodeList = aspectElement.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (isAdviceNode(node)) {
				AbstractBeanDefinition adviceDefinition = parseAdvice(aspectName, i, (Element) node, registry);
				Object pointcut = adviceDefinition.getPropertyValues().getPropertyValue(POINTCUT).getValue();
				if (pointcut instanceof RuntimeBeanReference) {
					beanReferences.add(pointcut);
				}
				beanDefinitions.add(adviceDefinition);
			}
		}
		fireAspectEvent(aspectElement, aspectId, beanDefinitions, beanReferences, parserContext);
	}

	private void fireAspectEvent(Element aspectElement, String aspectId, List beanDefinitions, List beanReferences, ParserContext parserContext) {
		BeanDefinition[] finalBeanDefinitions = (BeanDefinition[]) beanDefinitions.toArray(new BeanDefinition[beanDefinitions.size()]);
		RuntimeBeanReference[] finalBeanReferences = (RuntimeBeanReference[]) beanReferences.toArray(new RuntimeBeanReference[beanReferences.size()]);

		AspectComponentDefinition acd = new AspectComponentDefinition(aspectElement, aspectId, finalBeanDefinitions, finalBeanReferences);
		parserContext.getReaderContext().fireComponentRegistered(acd);
	}

	/**
	 * Returns <code>true</code> if the supplied node describes an advice type. May be one of:
	 * '<code>before</code>', '<code>after</code>', '<code>after-returning</code>', '<code>after-throwing</code>'
	 * or '<code>around</code>'.
	 */
	private boolean isAdviceNode(Node aNode) {
		if (! (aNode instanceof Element)) {
			return false;
		}
		else {
			String name = aNode.getLocalName();
			return (BEFORE.equals(name) ||
							AFTER.equals(name) ||
							AFTER_RETURNING_ELEMENT.equals(name) ||
							AFTER_THROWING_ELEMENT.equals(name) ||
							AROUND.equals(name));
		}
	}

	/**
	 * Parse a '<code>declare-parents</code>' element and register the appropriate {@link DeclareParentsAdvisor} with
	 * the {@link BeanDefinitionRegistry} encapsulated in the supplied {@link ParserContext}.
	 */
	private AbstractBeanDefinition parseDeclareParents(String aspectName, Element declareParentsElement,
																										 ParserContext parserContext) {
		AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(DeclareParentsAdvisor.class).
						addConstructorArg(declareParentsElement.getAttribute(IMPLEMENT_INTERFACE)).
						addConstructorArg(declareParentsElement.getAttribute(TYPE_PATTERN)).
						addConstructorArg(declareParentsElement.getAttribute(DEFAULT_IMPL)).
						setSource(declareParentsElement).getBeanDefinition();
		String name = BeanDefinitionReaderUtils.generateBeanName(beanDefinition, parserContext.getRegistry(), false);
		parserContext.getRegistry().registerBeanDefinition(name, beanDefinition);
		return beanDefinition;
	}

	/**
	 * Parses one of '<code>before</code>', '<code>after</code>', '<code>after-returning</code>', '<code>after-throwing</code>'
	 * or '<code>around</code>' and registers the resulting {@link BeanDefinition} with the supplied {@link BeanDefinitionRegistry}.
	 * @return the generated advice {@link RootBeanDefinition}
	 */
	private AbstractBeanDefinition parseAdvice(String aspectName, int order, Element adviceElement, BeanDefinitionRegistry registry) {

		// create the properties for the advisor
		MutablePropertyValues advisorProperties = new MutablePropertyValues();

		// create the method factory bean
		RootBeanDefinition methodDefinition = new RootBeanDefinition(MethodLocatingFactoryBean.class);
		methodDefinition.getPropertyValues().addPropertyValue("targetBeanName", aspectName);
		methodDefinition.getPropertyValues().addPropertyValue("methodName", adviceElement.getAttribute("method"));

		// create instance factory definition
		RootBeanDefinition instanceFactoryDefinition = new RootBeanDefinition(BeanFactoryAspectInstanceFactory.class);
		instanceFactoryDefinition.getPropertyValues().addPropertyValue("aspectBeanName", aspectName);

		// create the advice
		boolean isAroundAdvice = false;

		// register the pointcut
		AbstractBeanDefinition adviceDefinition = createAdviceDefinition(adviceElement, advisorProperties,
						registry, aspectName, order, methodDefinition, instanceFactoryDefinition);

		// configure the advisor
		RootBeanDefinition advisorDefinition = new RootBeanDefinition(AspectJPointcutAdvisor.class);
		advisorDefinition.setSource(adviceElement);
		advisorDefinition.setPropertyValues(advisorProperties);
		advisorDefinition.getPropertyValues().addPropertyValue(ADVICE, adviceDefinition);

		// register the final advisor
		String id = BeanDefinitionReaderUtils.generateBeanName(advisorDefinition, registry, false);
		registry.registerBeanDefinition(id, advisorDefinition);

		return advisorDefinition;
	}

	/**
	 * Creates the {@link RootBeanDefinition} for a POJO advice bean. Also causes pointcut parsing to occur
	 * so that the pointcut may be associate with the advice bean. This same pointcut is also configured as
	 * the pointcut for the enclosing {@link Advisor} definition using the supplied {@link MutablePropertyValues}.
	 */
	private AbstractBeanDefinition createAdviceDefinition(Element adviceElement, MutablePropertyValues advisorProperties, BeanDefinitionRegistry registry, String aspectName, int order, RootBeanDefinition methodDefinition, RootBeanDefinition instanceFactoryDefinition) {
		boolean isAroundAdvice;
		String pointcutBeanName = parsePointcutProperty(adviceElement, advisorProperties, registry);

		RootBeanDefinition adviceDefinition = new RootBeanDefinition(getAdviceClass(adviceElement));
		adviceDefinition.setSource(adviceElement);

		isAroundAdvice = AROUND.equals(adviceElement.getLocalName());
		adviceDefinition.getPropertyValues().addPropertyValue(ASPECT_NAME_PROPERTY, aspectName);
		adviceDefinition.getPropertyValues().addPropertyValue(ASPECT_BEAN_PROPERTY, new RuntimeBeanReference(aspectName));
		adviceDefinition.getPropertyValues().addPropertyValue(DECLARATION_ORDER_PROPERTY, new Integer(order));
		if (adviceElement.hasAttribute(RETURNING)) {
			adviceDefinition.getPropertyValues().addPropertyValue(RETURNING_PROPERTY, adviceElement.getAttribute(RETURNING));
		}
		if (adviceElement.hasAttribute(THROWING)) {
			adviceDefinition.getPropertyValues().addPropertyValue(THROWING_PROPERTY, adviceElement.getAttribute(THROWING));
		}
		if (adviceElement.hasAttribute(ARG_NAMES)) {
			adviceDefinition.getPropertyValues().addPropertyValue(ARG_NAMES_PROPERTY, adviceElement.getAttribute(ARG_NAMES));
		}

		ConstructorArgumentValues cav = new ConstructorArgumentValues();
		cav.addIndexedArgumentValue(METHOD_INDEX, methodDefinition);
		cav.addIndexedArgumentValue(POINTCUT_INDEX, new RuntimeBeanReference(pointcutBeanName));
		cav.addIndexedArgumentValue(ASPECT_INSTANCE_FACTORY_INDEX, instanceFactoryDefinition);

		if (isAroundAdvice) {
			extendAdviceConstructorArgs(cav);
		}

		adviceDefinition.setConstructorArgumentValues(cav);
		return adviceDefinition;
	}

	private void extendAdviceConstructorArgs(ConstructorArgumentValues cav) {
		RootBeanDefinition discovererDefinition = new RootBeanDefinition(PrioritizedParameterNameDiscoverer.class);
		cav.addIndexedArgumentValue(PARAMETER_NAME_DISCOVERER, discovererDefinition);
	}

	private Class getAdviceClass(String kind) {
		if (BEFORE.equals(kind)) {
			return AspectJMethodBeforeAdvice.class;
		}
		else if (AFTER.equals(kind)) {
			return AspectJAfterAdvice.class;
		}
		else if (AFTER_RETURNING.equals(kind)) {
			return AspectJAfterReturningAdvice.class;
		}
		else if (AFTER_THROWING.equals(kind)) {
			return AspectJAfterThrowingAdvice.class;
		}
		else if (AROUND.equals(kind)) {
			return AspectJAroundAdvice.class;
		}
		else {
			throw new IllegalArgumentException("Unknown advice kind [" + kind + "].");
		}
	}

	/**
	 * Gets the advice implementation class corresponding to the supplied {@link Element}.
	 */
	private Class getAdviceClass(Element adviceElement) {
		String elementName = adviceElement.getLocalName();
		if (BEFORE.equals(elementName)) {
			return AspectJMethodBeforeAdvice.class;
		}
		else if (AFTER.equals(elementName)) {
			return AspectJAfterAdvice.class;
		}
		else if (AFTER_RETURNING_ELEMENT.equals(elementName)) {
			return AspectJAfterReturningAdvice.class;
		}
		else if (AFTER_THROWING_ELEMENT.equals(elementName)) {
			return AspectJAfterThrowingAdvice.class;
		}
		else if (AROUND.equals(elementName)) {
			return AspectJAroundAdvice.class;
		}
		else {
			throw new IllegalArgumentException("Unknown advice kind [" + elementName + "].");
		}
	}

	/**
	 * Parses the supplied <code>&lt;pointcut&gt;</code> and registers the resulting
	 * {@link Pointcut} with the {@link BeanDefinitionRegistry}.
	 */
	private AbstractBeanDefinition parsePointcut(Element pointcutElement, ParserContext parserContext) {
		String id = pointcutElement.getAttribute(ID);
		String expression = pointcutElement.getAttribute(EXPRESSION);

		AbstractBeanDefinition pointcutDefinition = createPointcutDefinition(expression);
		pointcutDefinition.setSource(pointcutElement);

		BeanDefinitionRegistry registry = parserContext.getRegistry();
		if (!StringUtils.hasText(id)) {
			id = BeanDefinitionReaderUtils.generateBeanName((AbstractBeanDefinition) pointcutDefinition, registry, false);
		}

		registry.registerBeanDefinition(id, pointcutDefinition);

		PointcutComponentDefinition componentDefinition = new PointcutComponentDefinition(id, pointcutDefinition, expression);
		parserContext.getReaderContext().fireComponentRegistered(componentDefinition);

		return pointcutDefinition;
	}

	/**
	 * Parses the <code>pointcut</code> or <code>pointcut-ref</code> attributes of the supplied
	 * {@link Element} and add a <code>pointcut</code> property as appropriate. Generates a
	 * {@link org.springframework.beans.factory.config.BeanDefinition} for the pointcut if necessary and returns its bean name,
	 * otherwise returns the bean name of the referred pointcut.
	 * @throws IllegalStateException if the {@link Element} includes both <code>pointcut</code>
	 *                               and <code>pointcut-ref</code> attributes.
	 */
	public String parsePointcutProperty(
					Element element, MutablePropertyValues mpvs, BeanDefinitionRegistry registry) {

		if (element.hasAttribute(POINTCUT) && element.hasAttribute(POINTCUT_REF)) {
			throw new IllegalStateException("Cannot define both 'pointcut' and 'pointcut-ref' on 'advisor' tag.");
		}
		else if (element.hasAttribute(POINTCUT)) {
			// create a pointcut for the anonymous pc and register it
			AbstractBeanDefinition pointcutDefinition = createPointcutDefinition(element.getAttribute(POINTCUT));
			pointcutDefinition.setSource(element.getAttributeNode(POINTCUT));
			String pointcutName =
							BeanDefinitionReaderUtils.generateBeanName((AbstractBeanDefinition) pointcutDefinition, registry, false);
			registry.registerBeanDefinition(pointcutName, pointcutDefinition);
			mpvs.addPropertyValue(POINTCUT, new RuntimeBeanReference(pointcutName));
			return pointcutName;
		}
		else if (element.hasAttribute(POINTCUT_REF)) {
			String pointcutRef = element.getAttribute(POINTCUT_REF);
			mpvs.addPropertyValue(POINTCUT, new RuntimeBeanReference(pointcutRef));
			return pointcutRef;
		}
		else {
			throw new IllegalStateException("Must define one of 'pointcut' or 'pointcut-ref' on 'advisor'.");
		}
	}

	/**
	 * Creates a {@link BeanDefinition} for the {@link AspectJExpressionPointcut} class using
	 * the supplied pointcut expression.
	 */
	protected AbstractBeanDefinition createPointcutDefinition(String expression) {
		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(AspectJExpressionPointcut.class);
		beanDefinition.setSingleton(false);
		beanDefinition.getPropertyValues().addPropertyValue(EXPRESSION, expression);
		return beanDefinition;
	}
}
