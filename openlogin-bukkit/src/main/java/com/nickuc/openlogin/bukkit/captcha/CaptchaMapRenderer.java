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

package com.sobble.pleasejustlogin.bukkit.captcha;

import org.bukkit.entity.Player;
import org.bukkit.map.*;

import java.awt.*;
import java.util.Random;

/**
 * Custom map renderer that displays CAPTCHA code on a Minecraft map.
 */
public class CaptchaMapRenderer extends MapRenderer {

    private final String captchaCode;
    private boolean rendered = false;

    public CaptchaMapRenderer(String captchaCode) {
        super(true); // contextual rendering
        this.captchaCode = captchaCode;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) return;

        // Clear canvas with white background
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, MapPalette.WHITE);
            }
        }

        // Draw border
        drawBorder(canvas);

        // Draw CAPTCHA text
        drawCaptchaText(canvas, captchaCode);

        // Add visual noise for security
        addNoise(canvas);

        rendered = true;
    }

    /**
     * Draw a border around the map.
     */
    private void drawBorder(MapCanvas canvas) {
        byte borderColor = MapPalette.DARK_GRAY;
        
        // Top and bottom borders
        for (int x = 0; x < 128; x++) {
            canvas.setPixel(x, 0, borderColor);
            canvas.setPixel(x, 1, borderColor);
            canvas.setPixel(x, 126, borderColor);
            canvas.setPixel(x, 127, borderColor);
        }
        
        // Left and right borders
        for (int y = 0; y < 128; y++) {
            canvas.setPixel(0, y, borderColor);
            canvas.setPixel(1, y, borderColor);
            canvas.setPixel(126, y, borderColor);
            canvas.setPixel(127, y, borderColor);
        }
    }

    /**
     * Draw the CAPTCHA text centered on the canvas.
     */
    private void drawCaptchaText(MapCanvas canvas, String text) {
        MapFont font = MinecraftFont.Font;
        
        // Calculate text width and center position
        int textWidth = 0;
        for (char c : text.toCharArray()) {
            textWidth += font.getWidth(String.valueOf(c)) + 2; // Add spacing
        }
        
        int startX = (128 - textWidth) / 2;
        int startY = 55; // Center vertically
        
        // Draw each character with spacing
        int currentX = startX;
        for (char c : text.toCharArray()) {
            canvas.drawText(currentX, startY, font, "§0" + c);
            currentX += font.getWidth(String.valueOf(c)) + 2;
        }
        
        // Draw title
        String title = "CAPTCHA";
        int titleWidth = font.getWidth(title);
        canvas.drawText((128 - titleWidth) / 2, 20, font, title);
    }

    /**
     * Add visual noise to make OCR more difficult.
     */
    private void addNoise(MapCanvas canvas) {
        Random random = new Random();
        
        // Add random noise pixels
        for (int i = 0; i < 150; i++) {
            int x = random.nextInt(126) + 1;
            int y = random.nextInt(126) + 1;
            canvas.setPixel(x, y, MapPalette.LIGHT_GRAY);
        }
        
        // Add random lines
        for (int i = 0; i < 5; i++) {
            int x1 = random.nextInt(126) + 1;
            int y1 = random.nextInt(126) + 1;
            int x2 = random.nextInt(126) + 1;
            int y2 = random.nextInt(126) + 1;
            drawLine(canvas, x1, y1, x2, y2, MapPalette.LIGHT_GRAY);
        }
    }

    /**
     * Draw a line on the canvas using Bresenham's algorithm.
     */
    private void drawLine(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x1 >= 0 && x1 < 128 && y1 >= 0 && y1 < 128) {
                canvas.setPixel(x1, y1, color);
            }

            if (x1 == x2 && y1 == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }
}
