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

package org.springframework.orm.jpa.config;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.core.Conventions;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.LocalEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.vendor.TopLinkJpaVendorAdapter;
import org.springframework.util.StringUtils;

/**
 * @author Costin Leau
 * @since 2.0
 */
public class JpaNamespaceHandler extends NamespaceHandlerSupport {

	public void init() {
		registerBeanDefinitionParser("entityManagerFactory", new ConfigBeanDefinitionParser());
	}


	private class ConfigBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

		private static final String VENDOR = "vendor";

		private static final String VENDOR_PROVIDED = "provided";

		private static final String VENDOR_CUSTOM = "custom";

		private static final String ID_ATTRIBUTE = "id";

		private static final String LOAD_TIME_WEAVER = "load-time-weaver";

		protected Class getBeanClass(Element element) {
			return LocalEntityManagerFactoryBean.class;
		}

		protected void doParse(Element element, BeanDefinitionBuilder builder) {
			configureAttributes(element, builder);

			NodeList childNodes = element.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i++) {
				Node node = childNodes.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					String localName = node.getLocalName();
					if (VENDOR.equals(localName)) {
						parseVendor((Element) node, builder);
					}
				}
			}
		}

		private void configureAttributes(Element element, BeanDefinitionBuilder builder) {
			NamedNodeMap attributes = element.getAttributes();
			for (int x = 0; x < attributes.getLength(); x++) {
				Attr attribute = (Attr) attributes.item(x);
				String name = attribute.getLocalName();

				if (ID_ATTRIBUTE.equals(name)) {
					continue;
				}
				if (LOAD_TIME_WEAVER.equals(name)) {
					builder.getBeanDefinition().setBeanClass(LocalContainerEntityManagerFactoryBean.class);
				}

				builder.addPropertyValue(extractPropertyName(name), attribute.getValue());
			}
		}

		private String extractPropertyName(String attributeName) {
			return Conventions.attributeNameToPropertyName(attributeName);
		}

		private void parseVendor(Element element, BeanDefinitionBuilder builder) {
			BeanDefinitionBuilder vendorBuilder = BeanDefinitionBuilder.rootBeanDefinition(JpaVendorAdapter.class);
			configureAttributes(element, vendorBuilder);

			NodeList childNodes = element.getChildNodes();
			for (int i = 0; i < childNodes.getLength(); i++) {
				Node node = childNodes.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					String localName = node.getLocalName();
					if (VENDOR_PROVIDED.equals(localName)) {
						String name = ((Element) node).getAttribute("name");
						if (StringUtils.hasText(name)) {
							if ("toplink".equals(name))
								vendorBuilder.getBeanDefinition().setBeanClass(TopLinkJpaVendorAdapter.class);
							else if ("hibernate".equals(name))
								vendorBuilder.getBeanDefinition().setBeanClass(HibernateJpaVendorAdapter.class);
						}
					}
					else if (VENDOR_CUSTOM.equals(localName)) {
						String clazz = ((Element) node).getAttribute("class");
						if (StringUtils.hasText(clazz))
							vendorBuilder.getBeanDefinition().setBeanClassName(clazz);
					}
				}
			}

			builder.addPropertyValue("jpaVendorAdapter", vendorBuilder.getBeanDefinition());
		}
	}

}
