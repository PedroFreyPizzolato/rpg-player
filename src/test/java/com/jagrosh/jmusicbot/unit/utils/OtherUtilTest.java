/*
 * Copyright 2025 Arif Banai <a.banai@gmail.com>.
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
package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.utils.OtherUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OtherUtilTest
{
    @ParameterizedTest(name = "{index}: isNewerVersion({0}, {1}) should be {2} - {3}")
    @MethodSource("testData")
    public void testIsNewerVersion(String current, String latest, boolean expected, String reason)
    {
        assertEquals(expected, OtherUtil.isNewerVersion(current, latest), reason);
    }

    private static Stream<Arguments> testData()
    {
        return Stream.of(
                // Newer versions
                Arguments.of("0.5.1", "1.0.0", true, "Latest is newer (major)"),
                Arguments.of("0.5.1", "0.6.0", true, "Latest is newer (minor)"),
                Arguments.of("0.5.1", "0.5.2", true, "Latest is newer (patch)"),

                // Equal versions
                Arguments.of("0.5.1", "0.5.1", false, "Versions are equal"),

                // Older versions (User is ahead)
                Arguments.of("0.5.2", "0.5.1", false, "Current is newer (patch)"),
                Arguments.of("0.6.0", "0.5.1", false, "Current is newer (minor)"),

                // Edge cases
                Arguments.of("UNKNOWN", "0.5.1", true, "Unknown version should prompt update"),
                Arguments.of("0.5.1-RELEASE", "0.5.1", false, "Handles non-numeric suffixes (equal)"),
                Arguments.of("0.5.1", "0.5.2-BETA", true, "Handles suffixes in latest (newer)")
        );
    }
}
