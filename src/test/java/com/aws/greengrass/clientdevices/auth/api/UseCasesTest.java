/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.inject.Inject;
import java.net.URISyntaxException;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith({MockitoExtension.class, GGExtension.class})
public class UseCasesTest {
    private Topics topics;
    private UseCases useCases;

    static class TestDependency {
        private final String name;

        public TestDependency(String name) {
            this.name = name;
        }
    }

    static class UseCaseWithDependencies implements UseCases.UseCase<String, Void, Exception> {
        private final TestDependency dep;

        @Inject
        public UseCaseWithDependencies(TestDependency dep) {
            this.dep = dep;
        }

        @Override
        public String apply(Void dto) {
            return dep.name;
        }
    }

    static class UseCaseWithExceptions implements UseCases.UseCase<Void, Void, InvalidConfigurationException> {

        @Override
        public Void apply(Void dto) throws InvalidConfigurationException {
            throw new InvalidConfigurationException("Explode");
        }
    }

    static class UseCaseWithParameters implements UseCases.UseCase<String, String, Exception> {

        @Override
        public String apply(String dto) {
            return dto;
        }
    }

    static class UseCaseUpdatingDependency implements UseCases.UseCase<String, Void, Exception> {
        private final CDAConfiguration configuration;

        @Inject
        public UseCaseUpdatingDependency(CDAConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public String apply(Void dto) {
            return configuration.getCertificateUri().get().toString();
        }
    }

    @BeforeEach
    void beforeEach() {
        topics = Topics.of(new Context(), CLIENT_DEVICES_AUTH_SERVICE_NAME, null);
        this.useCases = new UseCases();
        this.useCases.init(topics.getContext());
    }

    @Test
    void GIVEN_aUseCaseWithDependencies_WHEN_ran_THEN_itExecutesWithNoExceptions() {
        TestDependency aTestDependency = new TestDependency("Something");
        useCases.provide(TestDependency.class, aTestDependency);

        UseCaseWithDependencies useCase = useCases.get(UseCaseWithDependencies.class);
        assertEquals(useCase.apply(null), aTestDependency.name);
    }

    @Test
    void GIVEN_aUseCaseWithExceptions_WHEN_ran_THEN_itThrowsAnException() {
        UseCaseWithExceptions useCase = useCases.get(UseCaseWithExceptions.class);
        assertThrows(InvalidConfigurationException.class, () -> { useCase.apply(null); });
    }

    @Test
    void GIVEN_aUseCaseWithParameters_WHEN_ran_itAcceptsTheParamsAndReturnsThem() {
        UseCaseWithParameters useCase = useCases.get(UseCaseWithParameters.class);
        assertEquals(useCase.apply("hello"), "hello");
    }

    @Test
    void Given_dependencyChanges_WHEN_ran_THEN_newInstanceIsProvided() throws URISyntaxException {
        // When
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue("file:///cert-uri");

        // Then
        useCases.provide(CDAConfiguration.class, CDAConfiguration.from(topics));
        UseCaseUpdatingDependency useCase = useCases.get(UseCaseUpdatingDependency.class);
        assertEquals(useCase.apply(null), "file:///cert-uri");

        // When
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue("file:///cert-changed-uri");

        // Then
        useCases.provide(CDAConfiguration.class, CDAConfiguration.from(topics));
        useCase = useCases.get(UseCaseUpdatingDependency.class);
        assertEquals(useCase.apply(null), "file:///cert-changed-uri");
    }
}