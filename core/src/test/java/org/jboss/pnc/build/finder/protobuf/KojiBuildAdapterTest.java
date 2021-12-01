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
package org.jboss.pnc.build.finder.protobuf;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;

class KojiBuildAdapterTest {
    private static final EasyRandom EASY_RANDOM = new EasyRandom();

    @Test
    void testSerializeDeserializeKojiBuild() {
        KojiBuild kojiBuild = EASY_RANDOM.nextObject(KojiBuild.class);
        KojiBuildAdapter adapter = new KojiBuildAdapter();
        String json = adapter.getJsonData(kojiBuild);
        KojiBuild deserialized = adapter.create(json);

        assertThat(deserialized.getId()).isEqualTo(kojiBuild.getId());
    }
}
