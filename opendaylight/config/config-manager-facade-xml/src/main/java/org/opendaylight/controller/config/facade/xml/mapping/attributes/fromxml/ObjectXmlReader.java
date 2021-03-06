/*
 * Copyright (c) 2015, 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.facade.xml.mapping.attributes.fromxml;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import org.opendaylight.controller.config.facade.xml.mapping.IdentityMapping;
import org.opendaylight.controller.config.facade.xml.mapping.attributes.AttributeIfcSwitchStatement;
import org.opendaylight.controller.config.yangjmxgenerator.attribute.AttributeIfc;
import org.opendaylight.controller.config.yangjmxgenerator.attribute.JavaAttribute;
import org.opendaylight.controller.config.yangjmxgenerator.attribute.ListAttribute;
import org.opendaylight.controller.config.yangjmxgenerator.attribute.ListDependenciesAttribute;
import org.opendaylight.controller.config.yangjmxgenerator.attribute.TOAttribute;
import org.opendaylight.yangtools.yang.common.Revision;

public class ObjectXmlReader extends AttributeIfcSwitchStatement<AttributeReadingStrategy> {

    private String key;
    private Map<String, Map<Optional<Revision>, IdentityMapping>> identityMap;

    @SuppressWarnings("checkstyle:hiddenField")
    public Map<String, AttributeReadingStrategy> prepareReading(final Map<String, AttributeIfc> yangToAttrConfig,
            final Map<String, Map<Optional<Revision>, IdentityMapping>> identityMap) {
        Map<String, AttributeReadingStrategy> strategies = Maps.newHashMap();
        this.identityMap = identityMap;

        for (Entry<String, AttributeIfc> attributeEntry : yangToAttrConfig.entrySet()) {
            AttributeReadingStrategy strat = prepareReadingStrategy(attributeEntry.getKey(), attributeEntry.getValue());
            strategies.put(attributeEntry.getKey(), strat);
        }
        return strategies;
    }

    private AttributeReadingStrategy prepareReadingStrategy(final String attrKey, final AttributeIfc attributeIfc) {
        this.key = attrKey;
        return switchAttribute(attributeIfc);
    }

    @Override
    protected AttributeReadingStrategy caseJavaBinaryAttribute(final OpenType<?> openType) {
        return new SimpleBinaryAttributeReadingStrategy(getLastAttribute().getNullableDefault());
    }

    @Override
    protected AttributeReadingStrategy caseJavaUnionAttribute(final OpenType<?> openType) {
        String mappingKey = JavaAttribute.DESCRIPTION_OF_VALUE_ATTRIBUTE_FOR_UNION;
        return new SimpleUnionAttributeReadingStrategy(getLastAttribute().getNullableDefault(), mappingKey);
    }

    @Override
    public AttributeReadingStrategy caseJavaSimpleAttribute(final SimpleType<?> openType) {
        return new SimpleAttributeReadingStrategy(getLastAttribute().getNullableDefault());
    }

    @Override
    public AttributeReadingStrategy caseJavaArrayAttribute(final ArrayType<?> openType) {
        SimpleAttributeReadingStrategy innerStrategy = new SimpleAttributeReadingStrategy(
                getLastAttribute().getNullableDefault());
        return new ArrayAttributeReadingStrategy(getLastAttribute().getNullableDefault(), innerStrategy);
    }

    @Override
    public AttributeReadingStrategy caseJavaCompositeAttribute(final CompositeType openType) {
        Preconditions.checkState(openType.keySet().size() == 1,
                "Unexpected number of elements for open type %s, should be 1", openType);
        String mappingKey = openType.keySet().iterator().next();
        return new SimpleCompositeAttributeReadingStrategy(getLastAttribute().getNullableDefault(), mappingKey);
    }

    @Override
    protected AttributeReadingStrategy caseJavaIdentityRefAttribute(final OpenType<?> openType) {
        Preconditions.checkState(openType instanceof CompositeType);
        Set<String> keys = ((CompositeType) openType).keySet();
        Preconditions.checkState(keys.size() == 1, "Unexpected number of elements for open type %s, should be 1",
                openType);
        String mappingKey = keys.iterator().next();
        return new SimpleIdentityRefAttributeReadingStrategy(getLastAttribute().getNullableDefault(), mappingKey,
                identityMap);
    }

    @Override
    protected AttributeReadingStrategy caseDependencyAttribute(final SimpleType<?> openType) {
        return new ObjectNameAttributeReadingStrategy(getLastAttribute().getNullableDefault());
    }

    @Override
    protected AttributeReadingStrategy caseTOAttribute(final CompositeType openType) {
        AttributeIfc lastAttribute = getLastAttribute();
        Preconditions.checkState(lastAttribute instanceof TOAttribute);
        Map<String, AttributeIfc> inner = ((TOAttribute) lastAttribute).getYangPropertiesToTypesMap();

        Map<String, AttributeReadingStrategy> innerStrategies = Maps.newHashMap();

        for (Entry<String, AttributeIfc> innerAttrEntry : inner.entrySet()) {
            AttributeReadingStrategy innerStrat = prepareReadingStrategy(innerAttrEntry.getKey(),
                    innerAttrEntry.getValue());
            innerStrategies.put(innerAttrEntry.getKey(), innerStrat);
        }
        return new CompositeAttributeReadingStrategy(lastAttribute.getNullableDefault(), innerStrategies);
    }

    @Override
    protected AttributeReadingStrategy caseListAttribute(final ArrayType<?> openType) {
        AttributeIfc lastAttribute = getLastAttribute();
        Preconditions.checkState(lastAttribute instanceof ListAttribute);
        AttributeReadingStrategy innerStrategy = prepareReadingStrategy(key,
                ((ListAttribute) lastAttribute).getInnerAttribute());
        return new ArrayAttributeReadingStrategy(lastAttribute.getNullableDefault(), innerStrategy);
    }

    @Override
    protected AttributeReadingStrategy caseListDependeciesAttribute(final ArrayType<?> openType) {
        AttributeIfc lastAttribute = getLastAttribute();
        Preconditions.checkState(lastAttribute instanceof ListDependenciesAttribute);
        AttributeReadingStrategy innerStrategy = caseDependencyAttribute(SimpleType.OBJECTNAME);
        return new ArrayAttributeReadingStrategy(lastAttribute.getNullableDefault(), innerStrategy);
    }
}
