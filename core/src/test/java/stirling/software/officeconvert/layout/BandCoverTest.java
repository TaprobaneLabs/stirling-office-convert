package stirling.software.officeconvert.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import stirling.software.officeconvert.extract.PageGraphics.Fill;

class BandCoverTest {

    private static Fill fill(float x, float right) {
        return new Fill(x, 100, right, 118, 0x205078);
    }

    @Test
    void overlappingFillsCountOnce() {
        assertEquals(40f, TableShaper.covered(List.of(fill(0, 40), fill(0, 40))), 0.01f);
        assertEquals(50f, TableShaper.covered(List.of(fill(10, 50), fill(0, 30))), 0.01f);
        assertEquals(60f, TableShaper.covered(List.of(fill(0, 30), fill(70, 100))), 0.01f);
    }
}
