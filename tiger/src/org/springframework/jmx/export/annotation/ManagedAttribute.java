/*
 * Copyright 2002-2005 the original author or authors.
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

package org.springframework.jmx.export.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JDK 1.5+ method-level annotation that indicates to expose a given bean
 * property as JMX attribute, corresponding to the ManagedAttribute attribute.
 * Only valid when used on a JavaBean getter or setter.
 * @author Rob Harrop
 * @since 1.2
 * @see org.springframework.jmx.export.metadata.ManagedAttribute
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedAttribute {

	String defaultValue() default "";

	String description() default "";

	int currencyTimeLimit() default -1;

	String persistPolicy() default "Never";

	int persistPeriod() default 0;

}
