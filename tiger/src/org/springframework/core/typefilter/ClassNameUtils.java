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

package org.springframework.core.typefilter;

/**
 * Simple utility methods for converting between internal and loadable classname formats.
 * 
 * @author Rod Johnson
 * @author Costin Leau
 * @author Mark Fisher
 * @since 2.1
 */
public abstract class ClassNameUtils {

	/**
	 * Convert the class name from the / form to the . form
	 * 
	 * @param className
	 * @return
	 */
	public static String convertInternalClassNameToLoadableClassName(String className) {
		return className.replace('/', '.');
	}
	
	/**
	 * Convert the class name from the . form to the / form
	 * 
	 * @param className
	 * @return
	 */
	public static String convertLoadableClassNameToInternalClassName(String className) {
		return className.replace('.', '/');
	}

}