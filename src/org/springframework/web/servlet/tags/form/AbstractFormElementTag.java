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

package org.springframework.web.servlet.tags.form;

import org.springframework.web.servlet.tags.HtmlEscapingAwareTag;
import org.springframework.web.util.ExpressionEvaluationUtils;

import javax.servlet.jsp.JspException;

/**
 * @author Rob Harrop
 * @since 2.0
 */
public abstract class AbstractFormElementTag extends HtmlEscapingAwareTag {

	protected final int doStartTagInternal() throws Exception {
		return writeTagContent(createTagWriter());
	}

	protected TagWriter createTagWriter() {
		return new TagWriter(this.pageContext.getOut());
	}

	protected Object evaluate(String attributeName, String value) throws JspException {
		if (value == null) {
			return null;
		}
		return ExpressionEvaluationUtils.evaluate(attributeName, value, this.pageContext);
	}

	protected abstract int writeTagContent(TagWriter tagWriter) throws JspException;
}
