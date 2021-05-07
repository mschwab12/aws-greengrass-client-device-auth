/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.StringLiteralAttribute;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

@Value
public class Thing implements AttributeProvider {
    public static final String NAMESPACE = "Thing";
    private static final String thingNamePattern = "[a-zA-Z0-9\\-_:]+";

    String thingName;

    /**
     * Constructor.
     * @param thingName AWS IoT ThingName
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public Thing(String thingName) {
        if (!Pattern.matches(thingNamePattern, thingName)) {
            throw new IllegalArgumentException("Invalid ThingName");
        }
        this.thingName = thingName;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("ThingName", new StringLiteralAttribute(thingName));
    }
}
