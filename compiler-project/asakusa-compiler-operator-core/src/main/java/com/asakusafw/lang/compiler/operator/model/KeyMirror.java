/**
 * Copyright 2011-2015 Asakusa Framework Team.
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
package com.asakusafw.lang.compiler.operator.model;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

import com.asakusafw.lang.compiler.operator.CompileEnvironment;
import com.asakusafw.lang.compiler.operator.util.AnnotationHelper;

/**
 * Represents a {@code Key} annotation model.
 */
public final class KeyMirror {

    private static final Pattern PATTERN_ORDER = Pattern.compile(
            "(\\w+)"                    // 1 - asc //$NON-NLS-1$
            + "|" + "(\\+\\s*(\\w+))"   // 3 - asc //$NON-NLS-1$ //$NON-NLS-2$
            + "|" + "(-\\s*(\\w+))"     // 5 - desc //$NON-NLS-1$ //$NON-NLS-2$
            + "|" + "((\\w+)\\s+ASC)"   // 7 - asc //$NON-NLS-1$ //$NON-NLS-2$
            + "|" + "((\\w+)\\s+DESC)"  // 9 - desc //$NON-NLS-1$ //$NON-NLS-2$
            + "", //$NON-NLS-1$
            Pattern.CASE_INSENSITIVE);

    private static final Map<Integer, Direction> ORDER_GROUP_DIRECTIONS;
    static {
        Map<Integer, Direction> map = new LinkedHashMap<>();
        map.put(1, Direction.ASCENDANT);
        map.put(3, Direction.ASCENDANT);
        map.put(5, Direction.DESCENDANT);
        map.put(7, Direction.ASCENDANT);
        map.put(9, Direction.DESCENDANT);
        ORDER_GROUP_DIRECTIONS = map;
    }

    private final AnnotationMirror source;

    private final List<Group> group;

    private final List<Order> order;

    private KeyMirror(AnnotationMirror source, List<Group> group, List<Order> order) {
        assert source != null;
        assert group != null;
        assert order != null;
        this.source = source;
        this.group = Collections.unmodifiableList(group);
        this.order = Collections.unmodifiableList(order);
    }

    /**
     * Returns the source annotation.
     * @return the source
     */
    public AnnotationMirror getSource() {
        return source;
    }

    /**
     * Returns the grouping key list.
     * @return the group
     */
    public List<Group> getGroup() {
        return group;
    }

    /**
     * Returns the ordering key list.
     * @return the order
     */
    public List<Order> getOrder() {
        return order;
    }

    /**
     * Parses the target {@code Key} annotation.
     * @param environment current environment
     * @param source the source annotation
     * @param annotationOwner annotated target
     * @param contextType target data model type
     * @return the parsed result, or {@code null} if failed to parse
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public static KeyMirror parse(
            CompileEnvironment environment,
            AnnotationMirror source,
            Element annotationOwner,
            DataModelMirror contextType) {
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null"); //$NON-NLS-1$
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null"); //$NON-NLS-1$
        }
        if (annotationOwner == null) {
            throw new IllegalArgumentException("annotationOwner must not be null"); //$NON-NLS-1$
        }
        if (contextType == null) {
            throw new IllegalArgumentException("contextType must not be null"); //$NON-NLS-1$
        }
        Map<String, AnnotationValue> pairs = AnnotationHelper.getValues(environment, source);
        if (pairs.containsKey("group") == false || pairs.containsKey("order") == false) {
            // May compilation failed
            return null;
        }
        boolean valid = true;
        List<AnnotationValue> groupValues = AnnotationHelper.toValueList(environment, pairs.get("group"));
        List<Group> groupList = new ArrayList<>();
        for (AnnotationValue groupValue : groupValues) {
            Group group = parseGroup(environment, source, groupValue, annotationOwner, contextType);
            if (group == null) {
                valid = false;
            } else {
                groupList.add(group);
            }
        }
        List<AnnotationValue> orderValues = AnnotationHelper.toValueList(environment, pairs.get("order"));
        List<Order> orderList = new ArrayList<>();
        for (AnnotationValue orderValue : orderValues) {
            Order order = parseOrder(environment, source, orderValue, annotationOwner, contextType);
            if (order == null) {
                valid = false;
            } else {
                orderList.add(order);
            }
        }
        if (valid == false) {
            return null;
        }
        return new KeyMirror(source, groupList, orderList);
    }

    private static Group parseGroup(
            CompileEnvironment environment,
            AnnotationMirror annotation,
            AnnotationValue annotationValue,
            Element contextElement,
            DataModelMirror contextType) {
        assert environment != null;
        assert annotation != null;
        assert annotationValue != null;
        assert contextElement != null;
        assert contextType != null;
        Object value = annotationValue.getValue();
        if ((value instanceof String) == false) {
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Invalid group format",
                    contextElement,
                    annotation,
                    annotationValue);
            return null;
        }
        String groupString = (String) value;
        String name = groupString.trim();
        PropertyMirror property = contextType.findProperty(name);
        if (property == null) {
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR,
                    MessageFormat.format(
                            "Unknown property \"{0}\"",
                            name),
                    contextElement,
                    annotation,
                    annotationValue);
            return null;
        }
        return new Group(annotation, annotationValue, property);
    }

    private static Order parseOrder(
            CompileEnvironment environment,
            AnnotationMirror annotation,
            AnnotationValue annotationValue,
            Element contextElement,
            DataModelMirror contextType) {
        assert environment != null;
        assert annotation != null;
        assert annotationValue != null;
        assert contextElement != null;
        assert contextType != null;
        Object value = annotationValue.getValue();
        if ((value instanceof String) == false) {
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Invalid order format",
                    contextElement,
                    annotation,
                    annotationValue);
            return null;
        }
        String orderString = (String) value;
        Matcher matcher = PATTERN_ORDER.matcher(orderString.trim());
        if (matcher.matches() == false) {
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Invalid order format",
                    contextElement,
                    annotation,
                    annotationValue);
            return null;
        }
        String name = null;
        Direction direction = null;
        for (Map.Entry<Integer, Direction> entry : ORDER_GROUP_DIRECTIONS.entrySet()) {
            int index = entry.getKey();
            String s = matcher.group(index);
            if (s != null) {
                name = s;
                direction = entry.getValue();
                break;
            }
        }
        assert name != null;
        assert direction != null;
        PropertyMirror property = contextType.findProperty(name);
        if (property == null) {
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR,
                    MessageFormat.format(
                            "Unknown property \"{0}\"",
                            name),
                    contextElement,
                    annotation,
                    annotationValue);
            return null;
        }
        return new Order(annotation, annotationValue, property, direction);
    }

    /**
     * Represents grouping.
     */
    public static class Group {

        private final AnnotationMirror annotation;

        private final AnnotationValue source;

        private final PropertyMirror property;

        Group(AnnotationMirror annotation, AnnotationValue source, PropertyMirror property) {
            assert annotation != null;
            assert source != null;
            assert property != null;
            this.annotation = annotation;
            this.source = source;
            this.property = property;
        }

        /**
         * Returns the declaring annotation.
         * @return the declaring annotation
         */
        public AnnotationMirror getAnnotation() {
            return annotation;
        }

        /**
         * Returns the representation source.
         * @return the source
         */
        public AnnotationValue getSource() {
            return source;
        }

        /**
         * Returns the target property.
         * @return the target property
         */
        public PropertyMirror getProperty() {
            return property;
        }
    }

    /**
     * Represents ordering.
     */
    public static class Order {

        private final AnnotationMirror annotation;

        private final AnnotationValue source;

        private final PropertyMirror property;

        private final Direction direction;

        Order(AnnotationMirror annotation, AnnotationValue source, PropertyMirror property, Direction direction) {
            assert annotation != null;
            assert source != null;
            assert property != null;
            assert direction != null;
            this.annotation = annotation;
            this.source = source;
            this.property = property;
            this.direction = direction;
        }

        /**
         * Returns the declaring annotation.
         * @return the declaring annotation
         */
        public AnnotationMirror getAnnotation() {
            return annotation;
        }

        /**
         * Returns the representation source.
         * @return the source
         */
        public AnnotationValue getSource() {
            return source;
        }

        /**
         * Returns the target property.
         * @return the target property
         */
        public PropertyMirror getProperty() {
            return property;
        }

        /**
         * Returns the ordering direction.
         * @return the ordering direction
         */
        public Direction getDirection() {
            return direction;
        }
    }

    /**
     * Represent ordering direction.
     */
    public enum Direction {

        /**
         * Ascendant ordering.
         */
        ASCENDANT,

        /**
         * Descendant ordering.
         */
        DESCENDANT,
    }
}
