/*
 * Copyright 2002-2004 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.rules.values;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Keith Donald
 */
public class DefaultFormModel implements FormModel {
    protected static final Log logger = LogFactory
            .getLog(DefaultFormModel.class);

    private MutableAspectAccessStrategy domainObjectAccessStrategy;

    private Object domainObject;

    private ValueModel domainObjectHolder;

    private ValueModel commitTrigger;

    private Map formValueModels = new HashMap();

    private boolean bufferChanges = true;

    private boolean hasErrors;

    public DefaultFormModel(Object domainObject) {
        this(new BeanPropertyAccessStrategy(domainObject));
    }

    public DefaultFormModel(ValueModel domainObjectHolder) {
        this(new BeanPropertyAccessStrategy(domainObjectHolder));
        this.domainObjectHolder = domainObjectHolder;
    }

    public DefaultFormModel(
            MutableAspectAccessStrategy domainObjectAccessStrategy) {
        this(domainObjectAccessStrategy, true);
    }

    public DefaultFormModel(
            MutableAspectAccessStrategy domainObjectAccessStrategy,
            boolean bufferChanges) {
        this.domainObjectAccessStrategy = domainObjectAccessStrategy;
        this.commitTrigger = new ValueHolder(null);
        this.bufferChanges = bufferChanges;
    }

    public void addValidationListener(ValidationListener listener) {

    }

    public void removeValidationListener(ValidationListener listener) {

    }

    public void setFormProperties(String[] domainObjectProperties) {
        formValueModels.clear();
        for (int i = 0; i < domainObjectProperties.length; i++) {
            add(domainObjectProperties[i]);
        }
    }

    public ValueModel getValueModel(String domainObjectProperty) {
        return (ValueModel)formValueModels.get(domainObjectProperty);
    }

    public boolean hasErrors() {
        return false;
    }

    protected Class getDomainObjectClass() {
        return domainObjectAccessStrategy.getDomainObject().getClass();
    }

    protected MutableAspectAccessStrategy getAccessStrategy() {
        return domainObjectAccessStrategy;
    }

    public ValueModel add(String domainObjectProperty) {
        ValueModel formValueModel = new AspectAdapter(domainObjectHolder,
                domainObjectAccessStrategy, domainObjectProperty);
        if (bufferChanges) {
            formValueModel = new BufferedValueModel(formValueModel,
                    commitTrigger);
        }
        formValueModels.put(domainObjectProperty, formValueModel);
        onNewFormValueModel(domainObjectProperty, formValueModel);
        return formValueModel;
    }

    protected void onNewFormValueModel(String domainObjectProperty,
            ValueModel formValueModel) {

    }

    public void commit() {
        if (bufferChanges) {
            if (hasErrors()) { throw new IllegalStateException(
                    "Form has errors; submit not allowed"); }
            commitTrigger.set(Boolean.TRUE);
            commitTrigger.set(null);
        }
    }

    public void revert() {
        if (bufferChanges) {
            commitTrigger.set(Boolean.FALSE);
            commitTrigger.set(null);
        }
    }
}