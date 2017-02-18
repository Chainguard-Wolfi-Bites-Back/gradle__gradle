/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.configuration.internal;

import org.gradle.api.Action;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.LocalBuildCache;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal {

    private final Instantiator instantiator;
    private final LocalBuildCache local;
    private BuildCache remote;
    private BuildCacheService testBuildCacheService;

    public DefaultBuildCacheConfiguration(Instantiator instantiator) {
        this.instantiator = instantiator;
        this.local = createBuildCacheConfiguration(LocalBuildCache.class);
    }

    @Override
    public LocalBuildCache getLocal() {
        return local;
    }

    @Override
    public void local(Action<? super LocalBuildCache> configuration) {
        configuration.execute(local);
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type) {
        return remote(type, Actions.doNothing());
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type, Action<? super BuildCache> configuration) {
        this.remote = createBuildCacheConfiguration(type);
        configuration.execute(remote);
        return Cast.uncheckedCast(remote);
    }

    @Override
    public BuildCache getRemote() {
        return remote;
    }

    @Override
    public void remote(Action<? super BuildCache> configuration) {
        configuration.execute(remote);
    }

    private <T extends BuildCache> T createBuildCacheConfiguration(Class<T> type) {
        return instantiator.newInstance(type);
    }

    @Override
    public BuildCacheService getBuildCacheServiceForTest() {
        return testBuildCacheService;
    }

    @Override
    public void setBuildCacheServiceForTest(BuildCacheService testBuildCacheService) {
        this.testBuildCacheService = testBuildCacheService;
    }
}
