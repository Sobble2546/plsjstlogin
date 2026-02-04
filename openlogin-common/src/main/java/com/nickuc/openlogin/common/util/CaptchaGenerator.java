/*
 * The MIT License (MIT)
 *
 * Copyright © 2020 - 2026 - OpenLogin Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sobble.pleasejustlogin.common.util;

import java.security.SecureRandom;

/**
 * Utility class for generating secure CAPTCHA codes.
 * Uses characters that are not easily confused (avoids 0/O, 1/I/l).
 */
public class CaptchaGenerator {

    // Character set avoiding ambiguous characters
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate a random CAPTCHA code of specified length.
     *
     * @param length the length of the CAPTCHA code
     * @return the generated CAPTCHA code
     */
    public static String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("CAPTCHA length must be positive");
        }

        StringBuilder captcha = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            captcha.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return captcha.toString();
    }
}
