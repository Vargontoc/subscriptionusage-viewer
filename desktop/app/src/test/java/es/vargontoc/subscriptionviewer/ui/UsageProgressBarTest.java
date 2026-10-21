package es.vargontoc.subscriptionviewer.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageProgressBarTest {

    @Test
    void paintsFortyPercentInGreenAndKeepsBlackText() {
        UsageProgressBar bar = new UsageProgressBar(40.0, "40 %");
        BufferedImage image = paint(bar, 102, 24);

        assertEquals(Color.GREEN.getRGB(), image.getRGB(1 + 19, 12));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(1 + 90, 12));
        assertEquals(40.0, bar.getPercentage());
        assertEquals("40 %", bar.getText());
    }

    @Test
    void zeroPercentHasNoFilledInterior() {
        BufferedImage image = paint(new UsageProgressBar(0.0, "0 %"), 102, 24);

        assertEquals(Color.WHITE.getRGB(), image.getRGB(1, 12));
    }

    @Test
    void oneHundredPercentIsBlack() {
        UsageProgressBar bar = new UsageProgressBar(100.0, "100 %");
        BufferedImage image = paint(bar, 102, 24);

        assertEquals(Color.BLACK.getRGB(), image.getRGB(50, 12));
    }

    @Test
    void displayedHundredDoesNotChangeColorOfNinetyNinePointNineSix() {
        UsageProgressBar bar = new UsageProgressBar(99.96, "100,0 %");
        BufferedImage image = paint(bar, 102, 24);

        assertEquals(Color.RED.getRGB(), image.getRGB(50, 12));
        assertEquals("100,0 %", bar.getText());
    }

    private static BufferedImage paint(UsageProgressBar bar, int width, int height) {
        bar.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            bar.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
