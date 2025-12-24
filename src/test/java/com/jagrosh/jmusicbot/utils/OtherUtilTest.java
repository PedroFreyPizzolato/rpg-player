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
package com.jagrosh.jmusicbot.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class OtherUtilTest
{
    private final String current;
    private final String latest;
    private final boolean expected;
    private final String reason;

    public OtherUtilTest(String current, String latest, boolean expected, String reason)
    {
        this.current = current;
        this.latest = latest;
        this.expected = expected;
        this.reason = reason;
    }

    @Parameters(name = "{index}: isNewerVersion({0}, {1}) should be {2} - {3}")
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][]{
                // Newer versions
                {"0.5.1", "1.0.0", true, "Latest is newer (major)"},
                {"0.5.1", "0.6.0", true, "Latest is newer (minor)"},
                {"0.5.1", "0.5.2", true, "Latest is newer (patch)"},

                // Equal versions
                {"0.5.1", "0.5.1", false, "Versions are equal"},

                // Older versions (User is ahead)
                {"0.5.2", "0.5.1", false, "Current is newer (patch)"},
                {"0.6.0", "0.5.1", false, "Current is newer (minor)"},

                // Edge cases
                {"UNKNOWN", "0.5.1", true, "Unknown version should prompt update"},
                {"0.5.1-RELEASE", "0.5.1", false, "Handles non-numeric suffixes (equal)"},
                {"0.5.1", "0.5.2-BETA", true, "Handles suffixes in latest (newer)"}
        });
    }

    @Test
    public void testIsNewerVersion()
    {
        assertEquals(reason, expected, OtherUtil.isNewerVersion(current, latest));
    }
}