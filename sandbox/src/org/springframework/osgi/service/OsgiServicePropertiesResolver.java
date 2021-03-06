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
 *
 * Created on 25-Jan-2006 by Adrian Colyer
 */
package org.springframework.osgi.service;

import java.util.Properties;

/**
 * An OsgiServicePropertiesResolver is responsible for providing
 * the properties that a bean exposed as a service will be published with. 
 * It is used as a collaborator of OsgiServiceExporter.
 * 
 * @see OsgiServiceExporter
 * @see BeanNameOsgiPropertiesResolver
 * 
 * @author Adrian Colyer
 * @since 2.0
 */
public interface OsgiServicePropertiesResolver {

	Properties getServiceProperties(Object bean, String beanName);
	
}
