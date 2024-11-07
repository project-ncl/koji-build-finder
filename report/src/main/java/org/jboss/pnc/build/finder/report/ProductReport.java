/*
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.build.finder.report;

import static j2html.TagCreator.attrs;
import static j2html.TagCreator.caption;
import static j2html.TagCreator.each;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.text;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static org.apache.commons.collections.IteratorUtils.unmodifiableIterator;
import static org.apache.commons.collections4.IteratorUtils.arrayListIterator;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import j2html.tags.ContainerTag;
import j2html.tags.specialized.TableTag;

public final class ProductReport extends Report {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductReport.class);

    private static final Pattern NAME_VER_PATTERN = Pattern.compile("(?<name>[\\p{Lower}\\-]+)(?<version>[0-9.?]+)?.*");

    private static final Pattern SPACE_PATTERN = Pattern.compile("-+");

    private static final Pattern JBOSS_PATTERN = Pattern.compile("jb|jboss");

    private static final int PRODUCT_NAME_LENGTH = 120;

    private final Map<String, List<String>> productMap;

    public ProductReport(File outputDirectory, Collection<KojiBuild> builds) {
        setName("Products");
        setDescription("List of builds partitioned by product (build target)");
        setBaseFilename("products");
        setOutputDirectory(outputDirectory);

        List<String> targets = builds.stream()
                .filter(build -> build.getBuildInfo() != null && build.getBuildInfo().getId() > 0)
                .filter(
                        build -> build.getTaskRequest() != null && build.getTaskRequest().asBuildRequest() != null
                                && build.getTaskRequest().asBuildRequest().getTarget() != null)
                .map(build -> build.getTaskRequest().asBuildRequest().getTarget())
                .toList();
        Map<String, Long> map = targets.stream().collect(Collectors.groupingBy(identity(), counting()));
        List<Entry<String, Long>> countList = map.entrySet()
                .stream()
                .sorted(Entry.<String, Long> comparingByValue().reversed())
                .toList();
        MultiValuedMap<String, KojiBuild> prodMap = new ArrayListValuedHashMap<>();

        for (KojiBuild build : builds) {
            if (build.getTaskRequest() == null || build.getTaskRequest().asBuildRequest() == null
                    || build.getTaskRequest().asBuildRequest().getTarget() == null) {
                continue;
            }

            for (Entry<String, Long> countEntry : countList) {
                String target = countEntry.getKey();

                if (build.getTaskRequest().asBuildRequest().getTarget().equals(target)) {
                    prodMap.put(targetToProduct(target), build);
                    break;
                }
            }
        }

        Map<String, Collection<KojiBuild>> pm = prodMap.asMap();
        Set<String> keySet = pm.keySet();
        int size = keySet.size();

        LOGGER.debug("Product List ({}):", size);

        this.productMap = new HashMap<>(size);
        Set<Entry<String, Collection<KojiBuild>>> entrySet = pm.entrySet();

        for (Entry<String, Collection<KojiBuild>> entry : entrySet) {
            String target = entry.getKey();
            Collection<KojiBuild> prodBuilds = entry.getValue();
            List<String> buildList = prodBuilds.stream().map(build -> build.getBuildInfo().getNvr()).toList();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{} ({}): {}", target, targetToProduct(target), buildList);
            }

            productMap.put(target, buildList);
        }
    }

    private static String targetToProduct(CharSequence tagName) {
        String prodName = JBOSS_PATTERN
                .matcher(
                        SPACE_PATTERN.matcher(NAME_VER_PATTERN.matcher(tagName).replaceAll("${name}-${version}"))
                                .replaceAll(" "))
                .replaceAll("JBoss");
        @SuppressWarnings("unchecked")
        Iterator<String> it = unmodifiableIterator(arrayListIterator(prodName.split(" ")));

        StringBuilder sb = new StringBuilder(PRODUCT_NAME_LENGTH);

        while (it.hasNext()) {
            String word = it.next();

            if (word.length() <= 4) {
                sb.append(word.toUpperCase(Locale.ENGLISH));
            } else {
                sb.append(String.valueOf(word.charAt(0)).toUpperCase(Locale.ENGLISH)).append(word.substring(1));
            }

            if (it.hasNext()) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    Map<String, List<String>> getProductMap() {
        return Collections.unmodifiableMap(productMap);
    }

    @Override
    public ContainerTag<TableTag> toHTML() {
        return table(
                attrs("#table-" + getBaseFilename()),
                caption(text(getName())),
                thead(tr(th(text("Product name")), th(text("Builds")))),
                tbody(
                        each(
                                this.productMap.entrySet(),
                                entry -> tr(td(text(entry.getKey())), td(text(String.join(", ", entry.getValue())))))));
    }
}
