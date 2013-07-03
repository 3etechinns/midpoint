/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.prism.query;

import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.lang.Validate;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.Itemable;
import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;

public class EqualsFilter extends PropertyValueFilter implements Itemable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 3284478412180258355L;

	EqualsFilter(){	
	}
	
	EqualsFilter(ItemPath parentPath, ItemDefinition definition, String matchingRule, List<PrismValue> values) {
		super(parentPath, definition, matchingRule, values);
	}

	EqualsFilter(ItemPath parentPath, ItemDefinition definition, String matchingRule, PrismValue value) {
		super(parentPath, definition, value);
	}
	
	EqualsFilter(ItemPath parentPath, ItemDefinition definition, Element expression) {
		super(parentPath, definition, expression);
	}
	
	EqualsFilter(ItemPath parentPath, ItemDefinition definition, String matchingRule, Element expression) {
		super(parentPath, definition, matchingRule, expression);
	}
	
	public static EqualsFilter createEqual(ItemPath parentPath, ItemDefinition itemDef, PrismValue value) {
		Validate.notNull(itemDef, "Item definition in the equals filter must not be null");
		return new EqualsFilter(parentPath, itemDef, null, value);
	}
	public static EqualsFilter createEqual(ItemPath parentPath, ItemDefinition itemDef, String matchingRule, PrismValue value) {
		Validate.notNull(itemDef, "Item definition in the equals filter must not be null");
		return new EqualsFilter(parentPath, itemDef, matchingRule, value);
	}

	public static EqualsFilter createEqual(ItemPath parentPath, ItemDefinition itemDef, String matchingRule, List<PrismValue> values) {
		Validate.notNull(itemDef, "Item definition in the equals filter must not be null");
		return new EqualsFilter(parentPath, itemDef, matchingRule, values);
	}

	public static EqualsFilter createEqual(ItemPath parentPath, ItemDefinition itemDef, Element expression) {
		Validate.notNull(itemDef, "Item definition in the equals filter must not be null");
		return new EqualsFilter(parentPath, itemDef, expression);
	}
	
	public static EqualsFilter createEqual(ItemPath parentPath, ItemDefinition itemDef, String matchingRule, Element expression) {
		Validate.notNull(itemDef, "Item definition in the equals filter must not be null");
		return new EqualsFilter(parentPath, itemDef, matchingRule, expression);
	}

	public static EqualsFilter createEqual(ItemPath parentPath, ItemDefinition item, String matchingRule, Object realValue) {
		return (EqualsFilter) createPropertyFilter(EqualsFilter.class, parentPath, item, matchingRule, realValue);
	}	
	
	public static EqualsFilter createEqual(ItemPath parentPath, PrismContainerDefinition<? extends Containerable> containerDef,
			QName propertyName, PrismValue... values) throws SchemaException {
		return (EqualsFilter) createPropertyFilter(EqualsFilter.class, parentPath, containerDef, propertyName, values);
	}

	public static EqualsFilter createEqual(ItemPath parentPath, PrismContainerDefinition<? extends Containerable> containerDef,
			QName propertyName, Object realValue) throws SchemaException {
		return (EqualsFilter) createPropertyFilter(EqualsFilter.class, parentPath, containerDef, propertyName, realValue);
	}

	public static EqualsFilter createEqual(Class<? extends Objectable> type, PrismContext prismContext, QName propertyName, Object realValue)
			throws SchemaException {
		return (EqualsFilter) createPropertyFilter(EqualsFilter.class, type, prismContext, propertyName, realValue);
	}
	
	public static EqualsFilter createEqual(Class<? extends Objectable> type, PrismContext prismContext, ItemPath propertyPath, Object realValue)
			throws SchemaException {
		return (EqualsFilter) createPropertyFilter(EqualsFilter.class, type, prismContext, propertyPath, realValue);
	}

    public static EqualsFilter createEqual(Class<? extends Objectable> type, PrismContext prismContext,
                                           QName propertyName, Object realValue, String matchingRule)
            throws SchemaException {
        EqualsFilter filter =  (EqualsFilter) createPropertyFilter(EqualsFilter.class, type, prismContext,
                propertyName, realValue);
        filter.setMatchingRule(matchingRule);

        return filter;
    }
	
	@Override
	public EqualsFilter clone() {
		EqualsFilter clone = new EqualsFilter(getParentPath(), getDefinition(), getMatchingRule(), (List<PrismValue>) getValues());
		cloneValues(clone);
		return clone;
	}

	@Override
	public String dump() {
		return debugDump(0);
	}

	@Override
	public String debugDump() {
		return debugDump(0);
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.indentDebugDump(sb, indent);
		sb.append("EQUALS:");
		
		if (getParentPath() != null){
			sb.append("\n");
			DebugUtil.indentDebugDump(sb, indent+1);
			sb.append("PATH: ");
			sb.append(getParentPath().toString());
		} 
		
		sb.append("\n");
		DebugUtil.indentDebugDump(sb, indent+1);
		sb.append("DEF: ");
		if (getDefinition() != null) {
			sb.append(getDefinition().toString());
		} else {
			sb.append("null");
		}
		
		sb.append("\n");
		DebugUtil.indentDebugDump(sb, indent+1);
		sb.append("VALUE:");
		if (getValues() != null) {
			sb.append("\n");
			for (PrismValue val : getValues()) {
				sb.append(DebugUtil.debugDump(val, indent + 2));
			}
		} else {
			sb.append(" null");
		}
		
		sb.append("\n");
		DebugUtil.indentDebugDump(sb, indent+1);
		sb.append("MATCHING: ");
		if (getMatchingRule() != null) {
			sb.append(getMatchingRule());
		} else {
			sb.append("default");
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("EQUALS: ");
		if (getParentPath() != null){
			sb.append(getParentPath().toString());
			sb.append(", ");
		}
		if (getDefinition() != null){
			sb.append(getDefinition().getName().getLocalPart());
			sb.append(", ");
		}
		if (getValues() != null){
			for (int i = 0; i< getValues().size() ; i++){
				PrismValue value = getValues().get(i);
				if (value == null) {
					sb.append("null");
				} else {
					sb.append(value.toString());
				}
				if ( i != getValues().size() -1){
					sb.append(", ");
				}
			}
		}
		return sb.toString();
	}

	@Override
	public QName getName() {
		return getDefinition().getName();
	}

	@Override
	public PrismContext getPrismContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ItemPath getPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends Objectable> boolean match(PrismObject<T> object) {
		return super.match(object);
	}

}
