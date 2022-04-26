/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.ipc;


import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.authorization.Permission;
import com.aws.greengrass.authorization.exceptions.AuthorizationException;
import com.aws.greengrass.device.ClientDevicesAuthService;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractVerifyClientDeviceIdentityOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.ClientDeviceCredential;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.eventstreamrpc.model.EventStreamJsonMessage;

import java.util.Optional;

import static com.aws.greengrass.ipc.common.ExceptionUtil.translateExceptions;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.VERIFY_CLIENT_DEVICE_IDENTITY;

public class VerifyClientDeviceIdentityOperationHandler
        extends GeneratedAbstractVerifyClientDeviceIdentityOperationHandler {
    private static final Logger logger = LogManager.getLogger(VerifyClientDeviceIdentityOperationHandler.class);
    private static final String COMPONENT_NAME = "componentName";
    private static final String UNAUTHORIZED_ERROR = "Not Authorized";
    private static final String NO_DEVICE_CREDENTIAL_ERROR = "Client device credential is required";
    private static final String NO_DEVICE_CERTIFICATE_ERROR = "Client device certificate is required";
    private final IotAuthClient iotAuthClient;
    private final String serviceName;
    private final AuthorizationHandler authorizationHandler;

    /**
     * Constructor.
     *
     * @param context              operation continuation handler
     * @param iotAuthClient        auth client for client device calls
     * @param authorizationHandler authorization handler
     */
    public VerifyClientDeviceIdentityOperationHandler(
            OperationContinuationHandlerContext context, IotAuthClient iotAuthClient,
            AuthorizationHandler authorizationHandler) {

        super(context);
        this.iotAuthClient = iotAuthClient;
        serviceName = context.getAuthenticationData().getIdentityLabel();
        this.authorizationHandler = authorizationHandler;
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.PreserveStackTrace"})
    @Override
    public VerifyClientDeviceIdentityResponse handleRequest(VerifyClientDeviceIdentityRequest request) {
        return translateExceptions(() -> {
            try {
                doAuthorizationForClientDevIdentity();
            } catch (AuthorizationException e) {
                logger.atWarn().kv("error", e.getMessage()).kv(COMPONENT_NAME, serviceName).log(UNAUTHORIZED_ERROR);
                throw new UnauthorizedError(e.getMessage());
            }
            String certificate = getCertificateFromCredential(request.getCredential());
            try {
                Optional<String> certificateId = iotAuthClient.getActiveCertificateId(certificate);
                VerifyClientDeviceIdentityResponse response = new VerifyClientDeviceIdentityResponse();
                return response.withIsValidClientDevice(certificateId.isPresent());
            } catch (Exception e) {
                logger.atError().cause(e).log("Unable to verify client device identity");
                throw new ServiceError(
                        "Verifying client device identity failed. Check Greengrass log for details.");
            }
        });
    }

    private void doAuthorizationForClientDevIdentity() throws AuthorizationException {
        authorizationHandler.isAuthorized(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME,
                Permission.builder()
                        .principal(serviceName)
                        .operation(VERIFY_CLIENT_DEVICE_IDENTITY)
                        .resource("*")
                        .build());
    }

    private String getCertificateFromCredential(ClientDeviceCredential credential) {
        if (credential == null) {
            throw new InvalidArgumentsError(NO_DEVICE_CREDENTIAL_ERROR);
        }
        String certificate = credential.getClientDeviceCertificate();
        if (Utils.isEmpty(certificate)) {
            throw new InvalidArgumentsError(NO_DEVICE_CERTIFICATE_ERROR);
        }
        return certificate;
    }

    @Override
    public void handleStreamEvent(EventStreamJsonMessage eventStreamJsonMessage) {

    }

    @Override
    protected void onStreamClosed() {

    }
}