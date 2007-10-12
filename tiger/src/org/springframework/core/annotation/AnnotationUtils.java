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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.Assert;

/**
 * <p>
 * General utility methods for working with annotations, handling bridge methods
 * (which the compiler generates for generic declarations) as well as super
 * methods (for optional &quot;annotation inheritance&quot;). Note that none of
 * this is provided by the JDK's introspection facilities themselves.
 * </p>
 * <p>
 * As a general rule for runtime-retained annotations (e.g. for transaction
 * control, authorization or service exposure), always use the lookup methods on
 * this class (e.g., {@link #findAnnotation(Method, Class)},
 * {@link #getAnnotation(Method, Class)}, and {@link #getAnnotations(Method)})
 * instead of the plain annotation lookup methods in the JDK. You can still
 * explicitly choose between lookup on the given class level only ({@link #getAnnotation(Method, Class)})
 * and lookup in the entire inheritance hierarchy of the given method ({@link #findAnnotation(Method, Class)}).
 * </p>
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Mark Fisher
 * @since 2.0
 * @see java.lang.reflect.Method#getAnnotations()
 * @see java.lang.reflect.Method#getAnnotation(Class)
 */
public abstract class AnnotationUtils {

	/** The attribute name for annotations with a single element. */
	static final String	VALUE	= "value";


	/**
	 * <p>
	 * Get all {@link Annotation Annotations} from the supplied {@link Method}.
	 * </p>
	 * <p>
	 * Correctly handles bridge {@link Method Methods} generated by the
	 * compiler.
	 * </p>
	 *
	 * @param method the method to look for annotations on
	 * @return the annotations found
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 */
	public static Annotation[] getAnnotations(final Method method) {

		return BridgeMethodResolver.findBridgedMethod(method).getAnnotations();
	}

	/**
	 * <p>
	 * Get a single {@link Annotation} of <code>annotationType</code> from the
	 * supplied {@link Method}.
	 * </p>
	 * <p>
	 * Correctly handles bridge {@link Method Methods} generated by the
	 * compiler.
	 * </p>
	 *
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation class to look for
	 * @return the annotations found
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 */
	public static <A extends Annotation> A getAnnotation(final Method method, final Class<A> annotationType) {

		return BridgeMethodResolver.findBridgedMethod(method).getAnnotation(annotationType);
	}

	/**
	 * <p>
	 * Get a single {@link Annotation} of <code>annotationType</code> from the
	 * supplied {@link Method}, traversing its super methods if no annotation
	 * can be found on the given method.
	 * </p>
	 * <p>
	 * Annotations on methods are not inherited by default, so we need to handle
	 * this explicitly.
	 * </p>
	 *
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation class to look for
	 * @return the annotation of the given type found, or <code>null</code>
	 */
	public static <A extends Annotation> A findAnnotation(Method method, final Class<A> annotationType) {

		if (!annotationType.isAnnotation()) {
			throw new IllegalArgumentException(annotationType + " is not an annotation");
		}
		A annotation = getAnnotation(method, annotationType);
		Class<?> cl = method.getDeclaringClass();
		while (annotation == null) {
			cl = cl.getSuperclass();
			if (cl == null || cl.equals(Object.class)) {
				break;
			}
			try {
				method = cl.getDeclaredMethod(method.getName(), method.getParameterTypes());
				annotation = getAnnotation(method, annotationType);
			}
			catch (final NoSuchMethodException ex) {
				// We're done...
			}
		}
		return annotation;
	}

	/**
	 * <p>
	 * Finds the first {@link Class} in the inheritance hierarchy of the
	 * specified <code>clazz</code> (including the specified
	 * <code>clazz</code> itself) which declares an annotation for the
	 * specified <code>annotationType</code>, or <code>null</code> if not
	 * found. If the supplied <code>clazz</code> is <code>null</code>,
	 * <code>null</code> will be returned.
	 * </p>
	 * <p>
	 * If the supplied <code>clazz</code> is an interface, only the interface
	 * itself will be checked; the inheritance hierarchy for interfaces will not
	 * be traversed.
	 * </p>
	 * <p>
	 * The standard {@link Class} API does not provide a mechanism for
	 * determining which class in an inheritance hierarchy actually declares an
	 * {@link Annotation}, so we need to handle this explicitly.
	 * </p>
	 *
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to
	 * check for the annotation, or <code>null</code>.
	 * @return the first {@link Class} in the inheritance hierarchy of the
	 * specified <code>clazz</code> which declares an annotation for the specified
	 * <code>annotationType</code>, or <code>null</code> if not found.
	 * @see Class#isAnnotationPresent(Class)
	 * @see Class#getDeclaredAnnotations()
	 */
	public static Class<?> findAnnotationDeclaringClass(
			final Class<? extends Annotation> annotationType, final Class<?> clazz) {

		Assert.notNull(annotationType, "annotationType must not be null");
		if ((clazz == null) || clazz.equals(Object.class)) {
			return null;
		}
		// else...
		return (isAnnotationDeclaredLocally(annotationType, clazz)) ? clazz : findAnnotationDeclaringClass(
				annotationType, clazz.getSuperclass());
	}

	/**
	 * <p>
	 * Returns <code>true</code> if an annotation for the specified
	 * <code>annotationType</code> is declared locally on the supplied
	 * <code>clazz</code>, else <code>false</code>. The supplied
	 * {@link Class} object may represent any type.
	 * </p>
	 * <p>
	 * Note: this method does <strong>not</strong> determine if the annotation
	 * is {@link java.lang.annotation.Inherited inherited}. For greater clarity
	 * regarding inherited annotations, consider using
	 * {@link #isAnnotationInherited(Class, Class)} instead.
	 * </p>
	 *
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to
	 * check for the annotation
	 * @return <code>true</code> if an annotation for the specified
	 * <code>annotationType</code> is declared locally on the supplied <code>clazz</code>
	 * @see Class#getDeclaredAnnotations()
	 * @see #isAnnotationInherited(Class, Class)
	 */
	public static boolean isAnnotationDeclaredLocally(
			final Class<? extends Annotation> annotationType, final Class<?> clazz) {

		Assert.notNull(annotationType, "annotationType must not be null");
		Assert.notNull(clazz, "clazz must not be null");
		boolean declaredLocally = false;
		for (final Annotation annotation : Arrays.asList(clazz.getDeclaredAnnotations())) {
			if (annotation.annotationType().equals(annotationType)) {
				declaredLocally = true;
				break;
			}
		}
		return declaredLocally;
	}

	/**
	 * <p>
	 * Returns <code>true</code> if an annotation for the specified
	 * <code>annotationType</code> is present on the supplied
	 * <code>clazz</code> and is
	 * {@link java.lang.annotation.Inherited inherited} (i.e., not declared
	 * locally for the class), else <code>false</code>.
	 * </p>
	 * <p>
	 * If the supplied <code>clazz</code> is an interface, only the interface
	 * itself will be checked. In accord with standard meta-annotation
	 * semantics, the inheritance hierarchy for interfaces will not be
	 * traversed. See the {@link java.lang.annotation.Inherited JavaDoc} for the
	 * &#064;Inherited meta-annotation for further details regarding annotation
	 * inheritance.
	 * </p>
	 *
	 * @param annotationType the Class object corresponding to the annotation type
	 * @param clazz the Class object corresponding to the class on which to
	 * check for the annotation
	 * @return <code>true</code> if an annotation for the specified
	 * <code>annotationType</code> is present on the supplied <code>clazz</code>
	 * and is {@link java.lang.annotation.Inherited inherited}
	 * @see Class#isAnnotationPresent(Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 */
	public static boolean isAnnotationInherited(
			final Class<? extends Annotation> annotationType, final Class<?> clazz) {

		Assert.notNull(annotationType, "annotationType must not be null");
		Assert.notNull(clazz, "clazz must not be null");
		return (clazz.isAnnotationPresent(annotationType) && !isAnnotationDeclaredLocally(annotationType, clazz));
	}

	/**
	 * Retrieve the given annotation's attributes as a Map.
	 *
	 * @param annotation the annotation to retrieve the attributes for
	 * @return the Map of annotation attributes, with attribute names as keys
	 * and corresponding attribute values as values
	 */
	public static Map<String, Object> getAnnotationAttributes(final Annotation annotation) {

		final Map<String, Object> attrs = new HashMap<String, Object>();
		final Method[] methods = annotation.annotationType().getDeclaredMethods();
		for (int j = 0; j < methods.length; j++) {
			final Method method = methods[j];
			if (method.getParameterTypes().length == 0 && method.getReturnType() != void.class) {
				try {
					attrs.put(method.getName(), method.invoke(annotation));
				}
				catch (final Exception ex) {
					throw new IllegalStateException("Could not obtain annotation attribute values", ex);
				}
			}
		}
		return attrs;
	}

	/**
	 * Retrieve the <em>value</em> of the <code>&quot;value&quot;</code>
	 * attribute of a single-element Annotation, given an annotation instance.
	 *
	 * @param annotation the annotation instance from which to retrieve the value
	 * @return the attribute value, or <code>null</code> if not found
	 * @see #getValue(Annotation, String)
	 */
	public static Object getValue(final Annotation annotation) {

		return getValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>value</em> of a named Annotation attribute, given an
	 * annotation instance.
	 *
	 * @see #getValue(Annotation)
	 * @param annotation the annotation instance from which to retrieve the value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the attribute value, or <code>null</code> if not found
	 */
	public static Object getValue(final Annotation annotation, final String attributeName) {

		try {
			final Method method = annotation.annotationType().getDeclaredMethod(attributeName, new Class[0]);
			return method.invoke(annotation);
		}
		catch (final Exception ex) {
			return null;
		}
	}

	/**
	 * Retrieve the <em>default value</em> of the
	 * <code>&quot;value&quot;</code> attribute of a single-element
	 * Annotation, given an annotation instance.
	 *
	 * @param annotation the annotation instance from which to retrieve
	 * the default value
	 * @return the default value, or <code>null</code> if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	public static Object getDefaultValue(final Annotation annotation) {

		return getDefaultValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named Annotation attribute,
	 * given an annotation instance.
	 *
	 * @param annotation the annotation instance from which to retrieve
	 * the default value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the default value of the named attribute, or <code>null</code>
	 * if not found.
	 * @see #getDefaultValue(Class, String)
	 */
	public static Object getDefaultValue(final Annotation annotation, final String attributeName) {

		return getDefaultValue(annotation.annotationType(), attributeName);
	}

	/**
	 * Retrieve the <em>default value</em> of the
	 * <code>&quot;value&quot;</code> attribute of a single-element
	 * Annotation, given the {@link Class annotation type}.
	 * @param annotationType the <em>annotation type</em> for which the
	 * default value should be retrieved
	 * @return the default value, or <code>null</code> if not found
	 * @see #getDefaultValue(Class, String)
	 */
	public static Object getDefaultValue(final Class<? extends Annotation> annotationType) {

		return getDefaultValue(annotationType, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named Annotation attribute,
	 * given the {@link Class annotation type}.
	 *
	 * @param annotationType the <em>annotation type</em> for which the
	 * default value should be retrieved
	 * @param attributeName the name of the attribute value to retrieve.
	 * @return the default value of the named attribute, or <code>null</code>
	 * if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	public static Object getDefaultValue(final Class<? extends Annotation> annotationType, final String attributeName) {

		try {
			final Method method = annotationType.getDeclaredMethod(attributeName, new Class[0]);
			return method.getDefaultValue();
		}
		catch (final Exception ex) {
			return null;
		}
	}

}
