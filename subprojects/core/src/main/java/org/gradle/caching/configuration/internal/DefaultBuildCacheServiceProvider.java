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

import org.gradle.StartParameter;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheServiceFactory;
import org.gradle.caching.internal.BuildOperationFiringBuildCacheServiceDecorator;
import org.gradle.caching.internal.LenientBuildCacheServiceDecorator;
import org.gradle.caching.internal.LoggingBuildCacheServiceDecorator;
import org.gradle.caching.internal.PushOrPullPreventingBuildCacheServiceDecorator;
import org.gradle.caching.internal.ShortCircuitingErrorHandlerBuildCacheServiceDecorator;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.progress.BuildOperationExecutor;

import java.io.IOException;

// TODO: Add some coverage
public class DefaultBuildCacheServiceProvider implements BuildCacheServiceProvider, Stoppable {
    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final StartParameter startParameter;
    private final BuildCacheServiceFactoryRegistry buildCacheServiceFactoryRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private BuildCacheService buildCacheService;

    public DefaultBuildCacheServiceProvider(BuildCacheConfigurationInternal buildCacheConfiguration, StartParameter startParameter, BuildCacheServiceFactoryRegistry buildCacheServiceFactoryRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.startParameter = startParameter;
        this.buildCacheServiceFactoryRegistry = buildCacheServiceFactoryRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public BuildCacheService getBuildCacheService() {
        if (buildCacheService == null) {
            buildCacheService = createBuildCacheService();
        }
        return buildCacheService;
    }

    private BuildCacheService createBuildCacheService() {
        BuildCache configuration = selectConfiguration();
        if (configuration != null) {
            return new LenientBuildCacheServiceDecorator(
                new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
                    3,
                    new LoggingBuildCacheServiceDecorator(
                        new PushOrPullPreventingBuildCacheServiceDecorator(
                            startParameter,
                            configuration,
                            new BuildOperationFiringBuildCacheServiceDecorator(
                                buildOperationExecutor,
                                createBuildCacheService(configuration)
                            )
                        )
                    )
                )
            );
        } else {
            return new NoOpBuildCacheService();
        }
    }

    private BuildCache selectConfiguration() {
        if (startParameter.isTaskOutputCacheEnabled()) {
            BuildCache remote = buildCacheConfiguration.getRemote();
            BuildCache local = buildCacheConfiguration.getLocal();
            if (remote != null && remote.isEnabled()) {
                return remote;
            } else if (local.isEnabled()) {
                return local;
            }
        }
        return null;
    }

    private <T extends BuildCache> BuildCacheService createBuildCacheService(T configuration) {
        if (buildCacheConfiguration.getBuildCacheServiceForTest()!=null) {
            return buildCacheConfiguration.getBuildCacheServiceForTest();
        }
        BuildCacheServiceFactory<T> buildCacheServiceFactory = Cast.uncheckedCast(buildCacheServiceFactoryRegistry.getBuildCacheServiceFactory(configuration.getClass()));
        return buildCacheServiceFactory.build(configuration);
    }

    @Override
    public void stop() {
        if (buildCacheService!=null) {
            CompositeStoppable.stoppable(buildCacheService).stop();
        }
    }

    private static class NoOpBuildCacheService implements BuildCacheService {
        @Override
        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return false;
        }

        @Override
        public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        }

        @Override
        public String getDescription() {
            return "NO-OP build cache";
        }

        @Override
        public void close() throws IOException {
            // Do nothing
        }
    };
}
